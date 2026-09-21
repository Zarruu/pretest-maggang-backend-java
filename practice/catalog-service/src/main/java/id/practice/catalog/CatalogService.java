package id.practice.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.practice.common.*;
import id.practice.common.Contracts.*;
import static id.practice.catalog.CatalogModels.*;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public CatalogService(JdbcTemplate db, ObjectMapper json) { this.db = db; this.json = json; }

    public Store store(UUID id) {
        return db.query("SELECT * FROM stores WHERE id=?", (r,n) -> new Store(r.getObject("id",UUID.class),r.getString("name"),r.getString("city")), id)
            .stream().findFirst().orElseThrow(() -> ApiException.missing("Toko tidak ditemukan"));
    }
    public PageResult<Store> stores(int page, int size) {
        return new PageResult<>(db.query("SELECT * FROM stores ORDER BY name,id LIMIT ? OFFSET ?", (r,n) ->
            new Store(r.getObject("id",UUID.class),r.getString("name"),r.getString("city")), size, (long)page*size),
            page,size,db.queryForObject("SELECT count(*) FROM stores",Long.class));
    }
    public Store createStore(StoreRequest req) {
        UUID id=UUID.randomUUID(); db.update("INSERT INTO stores(id,name,city) VALUES(?,?,?)",id,req.name(),req.city()); return store(id);
    }
    public Store updateStore(UUID id, StoreRequest req) {
        if(db.update("UPDATE stores SET name=?,city=? WHERE id=?",req.name(),req.city(),id)==0) throw ApiException.missing("Toko tidak ditemukan");
        return store(id);
    }
    public ProductDetail createProduct(ProductRequest req) {
        store(req.storeId()); UUID id=UUID.randomUUID();
        db.update("INSERT INTO products(id,store_id,name,description,category,image_url) VALUES(?,?,?,?,?,?)",id,req.storeId(),req.name(),req.description(),req.category(),req.imageUrl());
        return product(id);
    }
    public ProductDetail updateProduct(UUID id, ProductRequest req) {
        ProductDetail existing=product(id);
        if(!existing.product().storeId().equals(req.storeId())) throw ApiException.invalid("Produk tidak dapat dipindahkan ke toko lain");
        db.update("UPDATE products SET name=?,description=?,category=?,image_url=? WHERE id=?",req.name(),req.description(),req.category(),req.imageUrl(),id);
        return product(id);
    }
    public Variant createVariant(UUID productId, VariantRequest req) {
        product(productId); UUID id=UUID.randomUUID();
        db.update("INSERT INTO product_variants(id,product_id,sku,variant_name,price,stock) VALUES(?,?,?,?,?,?)",id,productId,req.sku(),req.variantName(),req.price(),req.stock());
        return variant(id);
    }
    public ProductDetail product(UUID id) {
        Product p=db.query("SELECT * FROM products WHERE id=?", (r,n) -> new Product(r.getObject("id",UUID.class),r.getObject("store_id",UUID.class),
            r.getString("name"),r.getString("description"),r.getString("category"),r.getString("image_url"),r.getObject("created_at",OffsetDateTime.class)),id)
            .stream().findFirst().orElseThrow(() -> ApiException.missing("Produk tidak ditemukan"));
        return new ProductDetail(p,store(p.storeId()),db.query(VARIANT_SQL+" WHERE v.product_id=? ORDER BY v.sku",this::mapVariant,id));
    }
    public PageResult<ProductSummary> products(UUID storeId, String q, String category, String sort, int page, int size) {
        StringBuilder filter=new StringBuilder(" WHERE 1=1"); List<Object> args=new ArrayList<>();
        if(storeId!=null){filter.append(" AND p.store_id=?");args.add(storeId);}
        if(q!=null&&!q.isBlank()){filter.append(" AND position(lower(?) in lower(p.name))>0");args.add(q.trim());}
        if(category!=null&&!category.isBlank()){filter.append(" AND p.category=?");args.add(category);}
        long total=db.queryForObject("SELECT count(*) FROM products p"+filter,Long.class,args.toArray());
        String ordering=switch(sort){case "newest" -> "p.created_at DESC,p.id";case "price_asc" -> "starting_price ASC NULLS LAST,p.id";
            case "price_desc" -> "starting_price DESC NULLS LAST,p.id";default -> throw ApiException.invalid("sort: newest, price_asc, atau price_desc");};
        args.add(size);args.add((long)page*size);
        String sql="SELECT p.*, (SELECT min(v.price) FROM product_variants v WHERE v.product_id=p.id) starting_price FROM products p"+filter+" ORDER BY "+ordering+" LIMIT ? OFFSET ?";
        List<ProductSummary> rows=db.query(sql,(r,n)->new ProductSummary(r.getObject("id",UUID.class),r.getObject("store_id",UUID.class),r.getString("name"),
            r.getString("category"),r.getString("image_url"),r.getObject("starting_price",Long.class),r.getObject("created_at",OffsetDateTime.class)),args.toArray());
        return new PageResult<>(rows,page,size,total);
    }
    public Variant variant(UUID id) {
        return db.query(VARIANT_SQL+" WHERE v.id=?",this::mapVariant,id).stream().findFirst().orElseThrow(()->ApiException.missing("Varian tidak ditemukan"));
    }
    @Transactional
    public StockResult purchase(UUID checkoutId, StockRequest request) {
        List<StockLine> lines=request.items().stream().sorted(Comparator.comparing(l->l.variantId().toString())).toList();
        if(lines.stream().map(StockLine::variantId).distinct().count()!=lines.size()) throw ApiException.invalid("Varian duplikat");
        // Serialize retries for one checkout; lock order below also prevents deadlocks across checkouts.
        db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",checkoutId.toString());
        String requestJson=write(new StockRequest(lines));
        var saved=db.queryForList("SELECT request_json::text,result_json::text FROM stock_operations WHERE checkout_id=?",checkoutId);
        if(!saved.isEmpty()) {
            if(!readTree(requestJson).equals(readTree((String)saved.get(0).get("request_json")))) throw ApiException.conflict("Checkout ID digunakan dengan item berbeda");
            try{return json.readValue((String)saved.get(0).get("result_json"),StockResult.class);}catch(JsonProcessingException ex){throw new IllegalStateException(ex);}
        }
        List<PurchasedLine> purchased=new ArrayList<>();
        for(StockLine line:lines) {
            Variant v=db.query(VARIANT_SQL+" WHERE v.id=? FOR UPDATE OF v",this::mapVariant,line.variantId()).stream().findFirst()
                .orElseThrow(()->ApiException.missing("Varian tidak ditemukan"));
            if(v.stock()<line.quantity()) throw ApiException.conflict("Stok tidak cukup: "+v.sku());
            db.update("UPDATE product_variants SET stock=stock-? WHERE id=?",line.quantity(),line.variantId());
            purchased.add(new PurchasedLine(v,line.quantity()));
        }
        StockResult result=new StockResult(purchased);
        db.update("INSERT INTO stock_operations(checkout_id,request_json,result_json) VALUES(?,?::jsonb,?::jsonb)",checkoutId,requestJson,write(result));
        return result;
    }
    private String write(Object value) { try{return json.writeValueAsString(value);}catch(JsonProcessingException ex){throw new IllegalStateException(ex);} }
    private com.fasterxml.jackson.databind.JsonNode readTree(String value) {try{return json.readTree(value);}catch(JsonProcessingException ex){throw new IllegalStateException(ex);}}
    private static final String VARIANT_SQL="SELECT v.*,p.store_id,p.name product_name,p.image_url,s.name store_name FROM product_variants v JOIN products p ON p.id=v.product_id JOIN stores s ON s.id=p.store_id";
    private Variant mapVariant(ResultSet r,int n) throws SQLException {
        return new Variant(r.getObject("id",UUID.class),r.getObject("product_id",UUID.class),r.getObject("store_id",UUID.class),r.getString("store_name"),
            r.getString("product_name"),r.getString("variant_name"),r.getString("sku"),r.getLong("price"),r.getInt("stock"),r.getString("image_url"));
    }
}
