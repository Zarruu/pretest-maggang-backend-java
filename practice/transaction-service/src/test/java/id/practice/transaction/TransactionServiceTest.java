package id.practice.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.practice.common.*;
import id.practice.common.Contracts.*;
import static id.practice.transaction.TransactionModels.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransactionServiceTest {
    JdbcTemplate db=mock(JdbcTemplate.class);
    ServiceClient client=mock(ServiceClient.class);
    TransactionService service=new TransactionService(db,client,new ObjectMapper(),"http://cart");
    UUID customer=UUID.randomUUID(),item=UUID.randomUUID();
    CheckoutRequest request(List<UUID> ids){return new CheckoutRequest(ids,"Buyer","08123","Bandung",Courier.REGULAR);}

    @Test void rejectsDuplicateSelectionBeforeAnySideEffect(){
        assertThatThrownBy(()->service.begin(customer,"key",request(List.of(item,item)))).isInstanceOf(ApiException.class).hasMessageContaining("duplikat");
        verifyNoInteractions(db,client);
    }
    @Test void retryUsesSavedTransactionEvenAfterCartWasCleared(){
        UUID transaction=UUID.randomUUID();CheckoutRequest req=request(List.of(item));
        when(db.queryForList(startsWith("SELECT id,request_json"),eq(customer),eq("key")))
            .thenReturn(List.of(Map.of("id",transaction,"request_json",service.write(req))));
        assertThat(service.begin(customer,"key",req)).isEqualTo(transaction);
        verifyNoInteractions(client);
    }
    @Test void sameKeyCannotBeUsedForDifferentAddress(){
        CheckoutRequest old=request(List.of(item));
        when(db.queryForList(startsWith("SELECT id,request_json"),eq(customer),eq("key")))
            .thenReturn(List.of(Map.of("id",UUID.randomUUID(),"request_json",service.write(old))));
        CheckoutRequest changed=new CheckoutRequest(List.of(item),"Buyer","08123","Jakarta",Courier.REGULAR);
        assertThatThrownBy(()->service.begin(customer,"key",changed)).isInstanceOf(ApiException.class).hasMessageContaining("berbeda");
        verifyNoInteractions(client);
    }
    @Test void cannotCheckoutAnotherCustomersItem(){
        when(db.queryForObject(startsWith("SELECT count(*)"),eq(Long.class),eq(customer))).thenReturn(0L);
        when(client.get("http://cart/internal/carts/"+customer,CartSnapshot.class)).thenReturn(new CartSnapshot(customer,List.of()));
        assertThatThrownBy(()->service.begin(customer,"key",request(List.of(item)))).isInstanceOf(ApiException.class).hasMessageContaining("pelanggan");
        verify(db,never()).update(startsWith("INSERT"),any(Object[].class));
    }
}
