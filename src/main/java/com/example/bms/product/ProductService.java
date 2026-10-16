package com.example.bms.product;

import com.example.bms.shared.PagedEnvelope;
import com.example.bms.shared.PageSize;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Product use cases (architecture §2: the application service is
 * transaction + load + authorize + save).
 *
 * <p>The permission split comes from business-rules §4, row "Create/edit/
 * deactivate product": ADMIN and MANAGER write, everybody signed in may
 * read — an EMPLOYEE picking a product on an order form needs the list.
 */
@Service
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;

    public ProductService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    /**
     * Deliberately no {@code @PreAuthorize}: this is the read side of the
     * split above. The URL layer still requires a signed-in caller.
     *
     * @param search the term the URL carried — handed to the boundary
     *               untouched (a blank term is no filter, and the boundary
     *               owns how matching works).
     */
    @Transactional(readOnly = true)
    public PagedEnvelope<ProductResponse> list(int page, int size, String search) {
        var safePage = Math.max(page, 0);
        var safeSize = Math.clamp(size, 1, PageSize.MAX_PAGE_SIZE);
        var items = products.findAll(safePage, safeSize, search).stream()
                .map(ProductService::responseOf)
                .toList();
        var total = products.count(search);
        var totalPages = (int) ((total + safeSize - 1) / safeSize);
        return new PagedEnvelope<>(items, safePage, safeSize, total, totalPages);
    }

    /**
     * Create: business-rules §4 row "Create/edit/deactivate product" —
     * ADMIN and MANAGER write, EMPLOYEE does not. The reads stay ungated
     * above; the URL layer requires a signed-in caller either way.
     *
     * <p>The category is loaded first so an unknown id answers 404
     * (api-design errors table) instead of becoming a foreign-key 500, and
     * its name travels back in the response — the UI renders what the API
     * answers rather than guessing it from the id it posted.
     *
     * <p>A product is born usable ({@code active} true) and at zero stock:
     * the mirror column only ever moves with an inventory movement
     * (ADR 0006), never with a form.
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public ProductResponse create(String sku, String name, String description,
                                  BigDecimal price, BigDecimal cost,
                                  Integer minimumStock, Long categoryId) {
        var category = categories.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        var stored = store(new Product(
                0, sku, name, description, price,
                cost == null ? BigDecimal.ZERO : cost,
                minimumStock == null ? 0 : minimumStock,
                0, categoryId, true));
        return responseOf(new ProductWithCategory(stored, category.name()));
    }

    /**
     * One write path — create, edit and the status flip. The SKU refusal
     * comes from V4's unique index — Postgres says no — and this is only
     * the translation into the 409 the UI renders inline. Nothing here
     * "checks first", so two identical POSTs answer identically and an
     * edit cannot race past the constraint either.
     */
    private Product store(Product product) {
        try {
            return products.save(product);
        } catch (DuplicateKeyException refused) {
            throw new ProductConflictException(product.sku());
        }
    }

    /**
     * Edit: load, refuse the unknown id as a 404, rebuild the row keeping
     * the fields no form writes — {@code stock} (ADR 0006: movements only)
     * and the id. The whole shape is required, so an edit cannot silently
     * blank a field the caller simply left out of the body.
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public ProductResponse edit(long id, String sku, String name, String description,
                                BigDecimal price, BigDecimal cost,
                                Integer minimumStock, Long categoryId) {
        var existing = products.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        var category = categories.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        var current = existing.product();
        var stored = store(new Product(
                current.id(), sku, name, description, price,
                cost == null ? BigDecimal.ZERO : cost,
                minimumStock == null ? 0 : minimumStock,
                current.stock(), categoryId, current.active()));
        return responseOf(new ProductWithCategory(stored, category.name()));
    }

    /**
     * The ticket's "change status", as a deactivation rather than a delete:
     * history references products (orders, movements), so retiring one has
     * to leave it in history (GLOSSARY "Deactivated").
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public ProductResponse changeStatus(long id, boolean active) {
        var existing = products.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        var stored = store(existing.product().withActive(active));
        return responseOf(new ProductWithCategory(stored, existing.categoryName()));
    }

    /** Entity + resolved category name → API response, in one place. */
    static ProductResponse responseOf(ProductWithCategory row) {
        var product = row.product();
        return new ProductResponse(
                product.id(), product.sku(), product.name(), product.description(),
                product.price(), product.cost(), product.minimumStock(), product.stock(),
                product.categoryId(), row.categoryName(), product.active());
    }
}
