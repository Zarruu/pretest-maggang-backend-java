package id.practice.cart;

import id.practice.common.Contracts.Variant;
import jakarta.validation.constraints.*;
import java.util.*;

public final class CartModels {
    private CartModels() {}
    public record AddItem(@NotNull UUID variantId,@Min(1) @Max(1000) int quantity,@NotNull @Size(max=500) String note) {}
    public record UpdateItem(@Min(1) @Max(1000) int quantity,@NotNull @Size(max=500) String note) {}
    public record Item(UUID id, Variant variant, int quantity, String note, long lineTotal) {}
    public record CartView(UUID customerId, List<Item> items, long subtotal) {}
}
