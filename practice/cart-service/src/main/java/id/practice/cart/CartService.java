package id.practice.cart;

import id.practice.common.*;
import id.practice.common.Contracts.*;
import static id.practice.cart.CartModels.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
    private final JdbcTemplate db;
    private final ServiceClient client;
    private final String catalogUrl;
    public CartService(JdbcTemplate db,ServiceClient client,@Value("${app.catalog-url}") String catalogUrl){this.db=db;this.client=client;this.catalogUrl=catalogUrl;}
    public CartSnapshot snapshot(UUID customer) {
        List<CartLine> items=db.query("SELECT i.* FROM cart_items i JOIN carts c ON c.id=i.cart_id WHERE c.customer_id=? ORDER BY i.id",
            (r,n)->new CartLine(r.getObject("id",UUID.class),r.getObject("variant_id",UUID.class),r.getInt("quantity"),r.getString("note"),r.getLong("version")),customer);
        return new CartSnapshot(customer,items);
    }
    public CartView view(UUID customer) {
        List<Item> result=new ArrayList<>(); long subtotal=0;
        for(CartLine line:snapshot(customer).items()) {
            Variant variant=variant(line.variantId()); long lineTotal=Math.multiplyExact(variant.price(),line.quantity());
            result.add(new Item(line.id(),variant,line.quantity(),line.note(),lineTotal)); subtotal=Math.addExact(subtotal,lineTotal);
        }
        return new CartView(customer,result,subtotal);
    }
    @Transactional
    public CartLine add(UUID customer, AddItem req) {
        Variant variant=variant(req.variantId()); UUID cart=lockCart(customer);
        var old=db.query("SELECT * FROM cart_items WHERE cart_id=? AND variant_id=?",(r,n)->new CartLine(r.getObject("id",UUID.class),r.getObject("variant_id",UUID.class),r.getInt("quantity"),r.getString("note"),r.getLong("version")),cart,req.variantId());
        int quantity=req.quantity()+(old.isEmpty()?0:old.get(0).quantity());
        if(quantity>1000) throw ApiException.invalid("Maksimal 1000 unit per varian");
        if(quantity>variant.stock()) throw ApiException.conflict("Stok tidak cukup");
        if(old.isEmpty()) {
            long count=db.queryForObject("SELECT count(*) FROM cart_items WHERE cart_id=?",Long.class,cart);
            if(count>=100) throw ApiException.invalid("Maksimal 100 varian per keranjang");
            UUID id=UUID.randomUUID(); db.update("INSERT INTO cart_items(id,cart_id,variant_id,quantity,note) VALUES(?,?,?,?,?)",id,cart,req.variantId(),quantity,req.note());
            return new CartLine(id,req.variantId(),quantity,req.note(),0);
        }
        CartLine previous=old.get(0);
        db.update("UPDATE cart_items SET quantity=?,note=?,version=version+1 WHERE id=?",quantity,req.note(),previous.id());
        return new CartLine(previous.id(),req.variantId(),quantity,req.note(),previous.version()+1);
    }
    @Transactional
    public CartLine update(UUID customer,UUID id,UpdateItem req) {
        UUID cart=lockCart(customer); CartLine previous=find(cart,id); Variant variant=variant(previous.variantId());
        if(req.quantity()>variant.stock()) throw ApiException.conflict("Stok tidak cukup");
        db.update("UPDATE cart_items SET quantity=?,note=?,version=version+1 WHERE id=?",req.quantity(),req.note(),id);
        return new CartLine(id,previous.variantId(),req.quantity(),req.note(),previous.version()+1);
    }
    @Transactional
    public void remove(UUID customer,UUID id) {
        UUID cart=lockCart(customer);
        if(db.update("DELETE FROM cart_items WHERE cart_id=? AND id=?",cart,id)==0) throw ApiException.missing("Item keranjang tidak ditemukan");
    }
    @Transactional
    public void cleanup(CleanupRequest req) {
        UUID cart=lockCart(req.customerId());
        // A user edit after checkout must not be deleted by a delayed retry.
        for(CleanupLine item:req.items()) db.update("DELETE FROM cart_items WHERE cart_id=? AND id=? AND version=?",cart,item.id(),item.version());
    }
    private UUID lockCart(UUID customer) {
        db.update("INSERT INTO carts(id,customer_id) VALUES(?,?) ON CONFLICT(customer_id) DO NOTHING",UUID.randomUUID(),customer);
        UUID id=db.queryForObject("SELECT id FROM carts WHERE customer_id=? FOR UPDATE",UUID.class,customer);
        db.update("UPDATE carts SET updated_at=now() WHERE id=?",id); return id;
    }
    private CartLine find(UUID cart,UUID id) {
        return db.query("SELECT * FROM cart_items WHERE cart_id=? AND id=?",(r,n)->new CartLine(r.getObject("id",UUID.class),r.getObject("variant_id",UUID.class),r.getInt("quantity"),r.getString("note"),r.getLong("version")),cart,id)
            .stream().findFirst().orElseThrow(()->ApiException.missing("Item keranjang tidak ditemukan"));
    }
    private Variant variant(UUID id){return client.get(catalogUrl+"/api/variants/"+id,Variant.class);}
}
