package com.example.bms.product;

import com.example.bms.shared.PagedEnvelope;
import com.example.bms.shared.PageSize;
import com.example.bms.shared.StatusRequest;
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

    @GetMapping
    public PagedEnvelope<ProductResponse> list(
            @RequestParam(defaultValue = "0") int page,
            // api-design size ≤ 100: the default and PageWindow's clamp share PageSize.
            @RequestParam(defaultValue = PageSize.DEFAULT_PAGE_SIZE) int size,
            // api-design: ILIKE over name and sku; blank (or absent) = no filter.
            @RequestParam(defaultValue = "") String search) {
        return products.list(page, size, search);
    }

    /** 200 with the updated row: the UI re-renders what the API answers. */
    @PutMapping("/{id}")
    public ProductResponse edit(@PathVariable long id,
                                @Valid @RequestBody ProductRequest request) {
        return products.edit(id, request);
    }

    /**
     * api-design: the deactivation convention every collection follows —
     * the shared {@link StatusRequest}. 200 with the updated row — same
     * shape as edit, so the UI has one path.
     */
    @PutMapping("/{id}/status")
    public ProductResponse changeStatus(@PathVariable long id,
                                        @RequestBody StatusRequest request) {
        return products.changeStatus(id, request.active());
    }

    /** 201, not 200: a caller must be able to tell a created row from an edited one. */
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        var created = products.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
