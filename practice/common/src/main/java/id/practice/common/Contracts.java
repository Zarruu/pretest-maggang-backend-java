package id.practice.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

/** Transport DTOs only; no shared persistence entities or cross-service repositories. */
public final class Contracts {
    private Contracts() {}
    public record Variant(UUID id, UUID productId, UUID storeId, String storeName, String productName,
                          String variantName, String sku, long price, int stock, String imageUrl) {}
    public record StockLine(@NotNull UUID variantId, @Min(1) @Max(1000) int quantity) {}
    public record StockRequest(@NotEmpty @Size(max=100) List<@Valid StockLine> items) {}
    public record PurchasedLine(Variant variant, int quantity) {}
    public record StockResult(List<PurchasedLine> items) {}
    public record CartLine(UUID id, UUID variantId, int quantity, String note, long version) {}
    public record CartSnapshot(UUID customerId, List<CartLine> items) {}
    public record CleanupLine(@NotNull UUID id, @Min(0) long version) {}
    public record CleanupRequest(@NotNull UUID customerId, @NotEmpty @Size(max=100) List<@Valid CleanupLine> items) {}
}
