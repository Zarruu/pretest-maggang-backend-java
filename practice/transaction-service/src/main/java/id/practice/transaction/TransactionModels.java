package id.practice.transaction;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;

public final class TransactionModels {
    private TransactionModels() {}
    public record CheckoutRequest(@NotEmpty @Size(max=100) List<@NotNull UUID> itemIds,
        @NotBlank @Size(max=150) String recipientName,@NotBlank @Size(max=30) String recipientPhone,
        @NotBlank @Size(max=2000) String shippingAddress,@NotNull Courier courier) {}
    public enum Courier {
        REGULAR(15000), EXPRESS(25000);
        private final long fee;
        Courier(long fee){this.fee=fee;}
        public long fee(){return fee;}
    }
    public record Item(UUID id,UUID variantId,String productName,String variantName,long unitPrice,int quantity,long lineTotal,String note) {}
    public record Order(UUID id,UUID storeId,String storeName,String invoiceNumber,String recipientName,
        String recipientPhone,String shippingAddress,String courier,long shippingCost,long subtotal,String status,List<Item> items) {}
    public record Detail(UUID id,UUID customerId,OffsetDateTime transactionDate,String status,long totalAmount,
        String failureReason,boolean cartCleaned,List<Order> orders) {}
    public record Summary(UUID id,OffsetDateTime transactionDate,String status,long totalAmount) {}
}
