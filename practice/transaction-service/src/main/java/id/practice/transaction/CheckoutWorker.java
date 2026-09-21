package id.practice.transaction;

import id.practice.common.ApiException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CheckoutWorker {
    private static final Logger log=LoggerFactory.getLogger(CheckoutWorker.class);
    private final PurchaseProcessor processor;
    private final TransactionService store;
    private final JdbcTemplate db;
    public CheckoutWorker(PurchaseProcessor processor,TransactionService store,JdbcTemplate db){this.processor=processor;this.store=store;this.db=db;}
    public void attempt(UUID id) {
        try {processor.fulfill(id);}
        catch(ApiException ex) {
            if(ex.status().is4xxClientError()) store.fail(id,ex.getMessage());
            else log.warn("Checkout {} pending: {}",id,ex.getMessage());
        } catch(RuntimeException ex){log.error("Checkout {} will retry",id,ex);}
        try {processor.cleanup(id);}catch(RuntimeException ex){log.warn("Cart cleanup {} will retry: {}",id,ex.getMessage());}
    }
    @Scheduled(fixedDelayString="${app.recovery-delay-ms}",initialDelayString="${app.recovery-delay-ms}")
    public void recover() {
        try {
            db.query("SELECT id FROM transactions WHERE status='PENDING' OR (status='CREATED' AND cart_cleaned=false) ORDER BY transaction_date LIMIT 20",(r,n)->r.getObject(1,UUID.class)).forEach(this::attempt);
        } catch(RuntimeException ex){log.warn("Checkout recovery delayed: {}",ex.getMessage());}
    }
}
