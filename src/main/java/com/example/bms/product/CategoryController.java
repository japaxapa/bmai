package com.example.bms.product;

import com.example.bms.shared.PagedEnvelope;
import com.example.bms.shared.PageSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
 * The categories HTTP surface (api-design: CRUD + status). Each verb arrives
 * with the test that pins it — this slice has only the read side.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    /**
     * The write DTO both verbs accept — create and rename post the very same
     * shape, so one record holds the field rules (one place to change them,
     * and create cannot drift from rename).
     */
    public record CategoryRequest(
            @NotBlank(message = "Name is required.")
            @Size(max = 120, message = "Name must be at most 120 characters.")
            String name) {
    }

    /** api-design: the deactivation convention every collection follows. */
    public record StatusRequest(boolean active) {
    }

    @GetMapping
    public PagedEnvelope<CategoryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            // api-design size ≤ 100: the default and PageWindow's clamp share PageSize.
            @RequestParam(defaultValue = PageSize.DEFAULT_PAGE_SIZE) int size) {
        return categories.list(page, size);
    }

    /** 201, not 200: a caller must be able to tell a created row from an edited one. */
    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        var created = categories.create(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** 200 with the updated row: the UI re-renders what the API answers. */
    @PutMapping("/{id}")
    public CategoryResponse edit(@PathVariable long id,
                                 @Valid @RequestBody CategoryRequest request) {
        return categories.edit(id, request.name());
    }

    /** 200 with the updated row — same shape as edit, so the UI has one path. */
    @PutMapping("/{id}/status")
    public CategoryResponse changeStatus(@PathVariable long id,
                                         @RequestBody StatusRequest request) {
        return categories.changeStatus(id, request.active());
    }
}
