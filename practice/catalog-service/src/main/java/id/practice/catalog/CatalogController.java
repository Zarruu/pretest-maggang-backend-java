package id.practice.catalog;

import id.practice.common.*;
import id.practice.common.Contracts.*;
import static id.practice.catalog.CatalogModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service){this.service=service;}
    @GetMapping("/api/stores") public PageResult<Store> stores(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return service.stores(page,size);}
    @GetMapping("/api/stores/{id}") public Store store(@PathVariable UUID id){return service.store(id);}
    @PostMapping("/api/stores") @ResponseStatus(HttpStatus.CREATED) public Store createStore(@Valid @RequestBody StoreRequest request){return service.createStore(request);}
    @PutMapping("/api/stores/{id}") public Store updateStore(@PathVariable UUID id,@Valid @RequestBody StoreRequest request){return service.updateStore(id,request);}
    @GetMapping("/api/products") public PageResult<ProductSummary> products(@RequestParam(required=false) UUID storeId,
        @RequestParam(required=false) @Size(max=200) String q,@RequestParam(required=false) @Size(max=100) String category,
        @RequestParam(defaultValue="newest") String sort,@RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return service.products(storeId,q,category,sort,page,size);}
    @GetMapping("/api/products/{id}") public ProductDetail product(@PathVariable UUID id){return service.product(id);}
    @PostMapping("/api/products") @ResponseStatus(HttpStatus.CREATED) public ProductDetail createProduct(@Valid @RequestBody ProductRequest request){return service.createProduct(request);}
    @PutMapping("/api/products/{id}") public ProductDetail updateProduct(@PathVariable UUID id,@Valid @RequestBody ProductRequest request){return service.updateProduct(id,request);}
    @PostMapping("/api/products/{id}/variants") @ResponseStatus(HttpStatus.CREATED) public Variant createVariant(@PathVariable UUID id,@Valid @RequestBody VariantRequest request){return service.createVariant(id,request);}
    @GetMapping("/api/variants/{id}") public Variant variant(@PathVariable UUID id){return service.variant(id);}
    @PostMapping("/internal/stock-operations/{checkoutId}") public StockResult purchase(@PathVariable UUID checkoutId,@Valid @RequestBody StockRequest request){return service.purchase(checkoutId,request);}
}
