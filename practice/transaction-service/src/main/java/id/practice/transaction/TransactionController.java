package id.practice.transaction;

import id.practice.common.PageResult;
import static id.practice.transaction.TransactionModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated
public class TransactionController {
    private final TransactionService service;
    private final CheckoutWorker worker;
    public TransactionController(TransactionService service,CheckoutWorker worker){this.service=service;this.worker=worker;}
    @PostMapping("/api/checkouts")
    public ResponseEntity<Detail> checkout(@RequestHeader("X-Customer-Id") UUID customer,
        @RequestHeader("Idempotency-Key") @NotBlank @Size(max=100) String key,@Valid @RequestBody CheckoutRequest request) {
        UUID id=service.begin(customer,key,request);worker.attempt(id);Detail result=service.detail(customer,id);
        HttpStatus status=switch(result.status()){case "CREATED"->HttpStatus.CREATED;case "FAILED"->HttpStatus.CONFLICT;default->HttpStatus.ACCEPTED;};
        return ResponseEntity.status(status).body(result);
    }
    @GetMapping("/api/transactions/{id}") public Detail detail(@RequestHeader("X-Customer-Id") UUID customer,@PathVariable UUID id){return service.detail(customer,id);}
    @GetMapping("/api/transactions") public PageResult<Summary> list(@RequestHeader("X-Customer-Id") UUID customer,
        @RequestParam(required=false) String status,@RequestParam(required=false) @Size(max=200) String q,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return service.list(customer,status,q,from,to,page,size);}
}
