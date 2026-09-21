package id.practice.transaction;

import id.practice.common.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;

class CheckoutWorkerTest {
    PurchaseProcessor processor=mock(PurchaseProcessor.class);
    TransactionService store=mock(TransactionService.class);
    CheckoutWorker worker=new CheckoutWorker(processor,store,mock(JdbcTemplate.class));
    UUID id=UUID.randomUUID();

    @Test void uncertainNetworkFailureRemainsPendingForRecovery(){
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"timeout")).when(processor).fulfill(id);
        worker.attempt(id);
        verify(store,never()).fail(any(),any());
    }
    @Test void definiteStockRejectionMarksCheckoutFailed(){
        doThrow(ApiException.conflict("Stok tidak cukup")).when(processor).fulfill(id);
        worker.attempt(id);
        verify(store).fail(id,"Stok tidak cukup");
    }
    @Test void cartCleanupFailureDoesNotUndoCompletedPurchase(){
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"timeout")).when(processor).cleanup(id);
        worker.attempt(id);
        verify(processor).fulfill(id);
        verify(store,never()).fail(any(),any());
    }
}
