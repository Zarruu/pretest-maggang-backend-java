package id.practice.transaction;

import id.practice.common.*;
import id.practice.common.Contracts.*;
import static id.practice.transaction.TransactionModels.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseProcessor {
    private final JdbcTemplate db;
    private final ServiceClient client;
    private final TransactionService store;
    private final String catalogUrl,cartUrl;
    public PurchaseProcessor(JdbcTemplate db,ServiceClient client,TransactionService store,
        @Value("${app.catalog-url}") String catalogUrl,@Value("${app.cart-url}") String cartUrl){this.db=db;this.client=client;this.store=store;this.catalogUrl=catalogUrl;this.cartUrl=cartUrl;}

    @Transactional
    public void fulfill(UUID id) {
        var rows=db.queryForList("SELECT status,request_json::text,cart_snapshot::text FROM transactions WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()||!"PENDING".equals(rows.get(0).get("status"))) return;
        var row=rows.get(0);CheckoutRequest request=store.read((String)row.get("request_json"),CheckoutRequest.class);
        CartSnapshot cart=store.read((String)row.get("cart_snapshot"),CartSnapshot.class);
        StockRequest stockRequest=new StockRequest(cart.items().stream().map(i->new StockLine(i.variantId(),i.quantity())).toList());
        // Catalog persists the result with the stock deduction. A retry returns that original result.
        StockResult result=client.post(catalogUrl+"/internal/stock-operations/"+id,stockRequest,StockResult.class);
        Map<UUID,List<PurchasedLine>> grouped=result.items().stream().collect(Collectors.groupingBy(i->i.variant().storeId()));
        Map<UUID,String> notes=cart.items().stream().collect(Collectors.toMap(CartLine::variantId,CartLine::note));
        long total=0;
        for(var entry:grouped.entrySet()) {
            UUID orderId=UUID.randomUUID();long subtotal=entry.getValue().stream().mapToLong(i->Math.multiplyExact(i.variant().price(),i.quantity())).sum();
            String invoice="INV-"+orderId.toString().toUpperCase(Locale.ROOT);
            db.update("INSERT INTO transaction_orders(id,transaction_id,store_id,store_name_snapshot,invoice_number,recipient_name,recipient_phone,shipping_address,courier,shipping_cost,subtotal) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                orderId,id,entry.getKey(),entry.getValue().get(0).variant().storeName(),invoice,request.recipientName(),request.recipientPhone(),request.shippingAddress(),request.courier().name(),request.courier().fee(),subtotal);
            for(PurchasedLine item:entry.getValue()) {
                Variant v=item.variant();
                db.update("INSERT INTO transaction_items(id,transaction_order_id,variant_id,product_name_snapshot,variant_name_snapshot,unit_price,quantity,line_total,note) VALUES(?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(),orderId,v.id(),v.productName(),v.variantName(),v.price(),item.quantity(),Math.multiplyExact(v.price(),item.quantity()),notes.get(v.id()));
            }
            total=Math.addExact(total,Math.addExact(subtotal,request.courier().fee()));
        }
        db.update("UPDATE transactions SET status='CREATED',total_amount=? WHERE id=?",total,id);
    }
    @Transactional
    public void cleanup(UUID id) {
        var rows=db.queryForList("SELECT cart_snapshot::text FROM transactions WHERE id=? AND status='CREATED' AND cart_cleaned=false FOR UPDATE",id);
        if(rows.isEmpty()) return;
        CartSnapshot cart=store.read((String)rows.get(0).get("cart_snapshot"),CartSnapshot.class);
        client.post(cartUrl+"/internal/carts/cleanup",new CleanupRequest(cart.customerId(),cart.items().stream().map(i->new CleanupLine(i.id(),i.version())).toList()),Void.class);
        db.update("UPDATE transactions SET cart_cleaned=true WHERE id=?",id);
    }
}
