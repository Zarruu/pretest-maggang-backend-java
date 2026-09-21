package id.practice.catalog;

import id.practice.common.Contracts.Variant;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;

public final class CatalogModels {
    private CatalogModels() {}
    public record Store(UUID id, String name, String city) {}
    public record StoreRequest(@NotBlank @Size(max=150) String name, @NotBlank @Size(max=100) String city) {}
    public record ProductRequest(@NotNull UUID storeId, @NotBlank @Size(max=200) String name,
        @NotBlank @Size(max=10000) String description, @NotBlank @Size(max=100) String category,
        @NotNull @Size(max=2000) String imageUrl) {}
    public record VariantRequest(@NotBlank @Size(max=80) String sku,
        @NotBlank @Size(max=150) String variantName, @Min(0) @Max(1000000000000L) long price,
        @Min(0) int stock) {}
    public record Product(UUID id, UUID storeId, String name, String description, String category,
                          String imageUrl, OffsetDateTime createdAt) {}
    public record ProductDetail(Product product, Store store, List<Variant> variants) {}
    public record ProductSummary(UUID id, UUID storeId, String name, String category, String imageUrl,
                                 Long startingPrice, OffsetDateTime createdAt) {}
}
