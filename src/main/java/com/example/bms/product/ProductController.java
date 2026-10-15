package com.example.bms.product;

import com.example.bms.shared.PagedEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

import java.math.BigDecimal;

/**
 * The products HTTP surface (api-design: CRUD + status + search). Each verb
 * arrives with the test that pins it — this slice has the read side and
 * create.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    /**
     * The write DTO create and edit share — one record holds the field
     * rules, so create cannot drift from edit. {@code description} is the
     * only optional column; cost and minimum stock are born zero when
     * omitted, and the category link is required (a product without one is
     * not a product — requirements: "Category: required on products"). The
     * business ranges (price > 0, cost ≥ 0, minimum stock ≥ 0) are the
     * domain's own refusals (422), not shape rules — they live on the
     * entity, not here.
     */
    public record ProductRequest(
            @NotBlank(message = "SKU is required.")
            String sku,
            @NotBlank(message = "Name is required.")
            String name,
            String description,
            @NotNull(message = "Price is required.")
            BigDecimal price,
            BigDecimal cost,
            Integer minimumStock,
            @NotNull(message = "A category is required.")
            Long categoryId) {
    }

    @GetMapping
    public PagedEnvelope<ProductResponse> list(
            @RequestParam(defaultValue = "0") int page,
            // Same rule as ProductService.MAX_PAGE_SIZE: api-design size ≤ 100.
            @RequestParam(defaultValue = "100") int size,
            // api-design: ILIKE over name and sku; blank (or absent) = no filter.
            @RequestParam(defaultValue = "") String search) {
        return products.list(page, size, search);
    }

    /** 200 with the updated row: the UI re-renders what the API answers. */
    @PutMapping("/{id}")
    public ProductResponse edit(@PathVariable long id,
                                @Valid @RequestBody ProductRequest request) {
        return products.edit(id,
                request.sku(), request.name(), request.description(),
                request.price(), request.cost(), request.minimumStock(), request.categoryId());
    }

    /** The deactivation convention (api-design), same shape as every resource. */
    public record StatusRequest(boolean active) {
    }

    /** 200 with the updated row — same shape as edit, so the UI has one path. */
    @PutMapping("/{id}/status")
    public ProductResponse changeStatus(@PathVariable long id,
                                        @RequestBody StatusRequest request) {
        return products.changeStatus(id, request.active());
    }

    /** 201, not 200: a caller must be able to tell a created row from an edited one. */
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        var created = products.create(
                request.sku(), request.name(), request.description(),
                request.price(), request.cost(), request.minimumStock(), request.categoryId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
