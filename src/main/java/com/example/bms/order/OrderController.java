package com.example.bms.order;

import com.example.bms.shared.PageSize;
import com.example.bms.shared.PagedEnvelope;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The orders HTTP surface (api-design: GET list, GET detail, POST, and PUT for
 * a draft only — no DELETE, ever). The lifecycle verbs after DRAFT
 * (confirm/start-processing/complete/cancel/return) belong to tickets #10+.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    public PagedEnvelope<OrderSummaryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = PageSize.DEFAULT_PAGE_SIZE) int size) {
        return orders.list(page, size);
    }

    @GetMapping("/{id}")
    public OrderResponse detail(@PathVariable long id) {
        return orders.get(id);
    }

    /** 201, not 200: the caller must be able to tell a created draft from an edited one. */
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orders.create(request));
    }

    /** PUT replaces the whole draft — lines, quantities and customer (draft only). */
    @PutMapping("/{id}")
    public OrderResponse edit(@PathVariable long id,
                              @Valid @RequestBody OrderRequest request) {
        return orders.edit(id, request);
    }
}
