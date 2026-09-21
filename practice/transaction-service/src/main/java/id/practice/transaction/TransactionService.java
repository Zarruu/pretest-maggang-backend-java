package id.practice.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.practice.common.*;
import id.practice.common.Contracts.*;
import static id.practice.transaction.TransactionModels.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class TransactionService {
    private final JdbcTemplate db;
    private final ServiceClient client;
    private final ObjectMapper json;
    private final String cartUrl;
    public TransactionService(JdbcTemplate db,ServiceClient client,ObjectMapper json,@Value("${app.cart-url}") String cartUrl){this.db=db;this.client=client;this.json=json;this.cartUrl=cartUrl;}

    @Transactional
    public UUID begin(UUID customer,String key,CheckoutRequest request) {
        if(request.itemIds().stream().distinct().count()!=request.itemIds().size()) throw ApiException.invalid("Item checkout duplikat");
        CheckoutRequest normalized=new CheckoutRequest(request.itemIds().stream().sorted().toList(),request.recipientName(),request.recipientPhone(),request.shippingAddress(),request.courier());
        String payload=write(normalized);
        db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",customer.toString());
        var existing=db.queryForList("SELECT id,request_json::text FROM transactions WHERE customer_id=? AND idempotency_key=?",customer,key);
        if(!existing.isEmpty()) {
            if(!tree(payload).equals(tree((String)existing.get(0).get("request_json")))) throw ApiException.conflict("Idempotency-Key sudah digunakan untuk request berbeda");
            return (UUID)existing.get(0).get("id");
        }
        long active=db.queryForObject("SELECT count(*) FROM transactions WHERE customer_id=? AND (status='PENDING' OR (status='CREATED' AND cart_cleaned=false))",Long.class,customer);
        if(active>0) throw ApiException.conflict("Checkout sebelumnya masih diproses; gunakan key sebelumnya atau tunggu selesai");
        CartSnapshot cart=client.get(cartUrl+"/internal/carts/"+customer,CartSnapshot.class);
        List<CartLine> chosen=cart.items().stream().filter(i->request.itemIds().contains(i.id())).toList();
        if(chosen.size()!=request.itemIds().size()) throw ApiException.invalid("Item tidak ditemukan di keranjang pelanggan");
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO transactions(id,customer_id,status,idempotency_key,request_json,cart_snapshot) VALUES(?,?,'PENDING',?,?::jsonb,?::jsonb)",
            id,customer,key,payload,write(new CartSnapshot(customer,chosen)));
        return id;
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Detail detail(UUID customer,UUID id) {
        var tx=db.queryForList("SELECT * FROM transactions WHERE id=? AND customer_id=?",id,customer);
        if(tx.isEmpty()) throw ApiException.missing("Transaksi tidak ditemukan");
        var row=tx.get(0);
        List<Order> orders=db.query("SELECT * FROM transaction_orders WHERE transaction_id=? ORDER BY id",(r,n)->new Order(r.getObject("id",UUID.class),r.getObject("store_id",UUID.class),
            r.getString("store_name_snapshot"),r.getString("invoice_number"),r.getString("recipient_name"),r.getString("recipient_phone"),r.getString("shipping_address"),r.getString("courier"),
            r.getLong("shipping_cost"),r.getLong("subtotal"),r.getString("status"),items(r.getObject("id",UUID.class))),id);
        OffsetDateTime date=db.queryForObject("SELECT transaction_date FROM transactions WHERE id=?",(r,n)->r.getObject(1,OffsetDateTime.class),id);
        return new Detail(id,customer,date,(String)row.get("status"),((Number)row.get("total_amount")).longValue(),(String)row.get("failure_reason"),(Boolean)row.get("cart_cleaned"),orders);
    }
    private List<Item> items(UUID orderId) {
        return db.query("SELECT * FROM transaction_items WHERE transaction_order_id=? ORDER BY id",(r,n)->new Item(r.getObject("id",UUID.class),r.getObject("variant_id",UUID.class),
            r.getString("product_name_snapshot"),r.getString("variant_name_snapshot"),r.getLong("unit_price"),r.getInt("quantity"),r.getLong("line_total"),r.getString("note")),orderId);
    }
    public PageResult<Summary> list(UUID customer,String status,String q,OffsetDateTime from,OffsetDateTime to,int page,int size) {
        StringBuilder filter=new StringBuilder(" WHERE t.customer_id=?");List<Object> args=new ArrayList<>();args.add(customer);
        if(status!=null) {
            if(!Set.of("PENDING","CREATED","FAILED").contains(status)) throw ApiException.invalid("Status tidak valid");
            filter.append(" AND t.status=?");args.add(status);
        }
        if(from!=null&&to!=null&&from.isAfter(to)) throw ApiException.invalid("from harus sebelum to");
        if(from!=null){filter.append(" AND t.transaction_date>=?");args.add(from);}
        if(to!=null){filter.append(" AND t.transaction_date<=?");args.add(to);}
        if(q!=null&&!q.isBlank()) {
            filter.append(" AND EXISTS(SELECT 1 FROM transaction_orders o JOIN transaction_items i ON i.transaction_order_id=o.id WHERE o.transaction_id=t.id AND (position(lower(?) in lower(o.invoice_number))>0 OR position(lower(?) in lower(i.product_name_snapshot))>0))");args.add(q.trim());args.add(q.trim());
        }
        long total=db.queryForObject("SELECT count(*) FROM transactions t"+filter,Long.class,args.toArray());args.add(size);args.add((long)page*size);
        var rows=db.query("SELECT t.* FROM transactions t"+filter+" ORDER BY transaction_date DESC,id LIMIT ? OFFSET ?",(r,n)->new Summary(r.getObject("id",UUID.class),
            r.getObject("transaction_date",OffsetDateTime.class),r.getString("status"),r.getLong("total_amount")),args.toArray());
        return new PageResult<>(rows,page,size,total);
    }
    @Transactional
    public void fail(UUID id,String reason) {db.update("UPDATE transactions SET status='FAILED',failure_reason=? WHERE id=? AND status='PENDING'",reason,id);}
    public String write(Object value){try{return json.writeValueAsString(value);}catch(JsonProcessingException ex){throw new IllegalStateException(ex);}}
    public <T> T read(String value,Class<T> type){try{return json.readValue(value,type);}catch(JsonProcessingException ex){throw new IllegalStateException(ex);}}
    private com.fasterxml.jackson.databind.JsonNode tree(String value){try{return json.readTree(value);}catch(JsonProcessingException ex){throw new IllegalStateException(ex);}}
}
