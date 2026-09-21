package id.practice.cart;

import id.practice.common.Contracts.*;
import static id.practice.cart.CartModels.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class CartController {
    private final CartService service;
    public CartController(CartService service){this.service=service;}
    @GetMapping("/api/cart") public CartView view(@RequestHeader("X-Customer-Id") UUID customer){return service.view(customer);}
    @PostMapping("/api/cart/items") @ResponseStatus(HttpStatus.CREATED)
    public CartLine add(@RequestHeader("X-Customer-Id") UUID customer,@Valid @RequestBody AddItem req){return service.add(customer,req);}
    @PatchMapping("/api/cart/items/{id}") public CartLine update(@RequestHeader("X-Customer-Id") UUID customer,@PathVariable UUID id,@Valid @RequestBody UpdateItem req){return service.update(customer,id,req);}
    @DeleteMapping("/api/cart/items/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestHeader("X-Customer-Id") UUID customer,@PathVariable UUID id){service.remove(customer,id);}
    @GetMapping("/internal/carts/{customer}") public CartSnapshot snapshot(@PathVariable UUID customer){return service.snapshot(customer);}
    @PostMapping("/internal/carts/cleanup") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cleanup(@Valid @RequestBody CleanupRequest req){service.cleanup(req);}
}
