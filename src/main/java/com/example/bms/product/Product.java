package com.example.bms.product;

import java.math.BigDecimal;

/**
 * A product row (GLOSSARY Product): a physical good we buy and resell,
 * identified by its SKU and belonging to exactly one category. Immutable —
 * an edit or a status flip produces a new instance, so the service hands
 * out values nobody can mutate behind its back.
 *
 * <p>The number shapes follow database-design: money is {@code numeric(12,2)}
 * ({@link BigDecimal}, never {@code double}), stock and minimum stock are
 * integer quantities. {@code stock} is the mirror column (ADR 0006): this
 * slice never writes it — movements will, from ticket #12 on.
 */
public record Product(
        long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        BigDecimal cost,
        int minimumStock,
        int stock,
        long categoryId,
        boolean active) {

    /**
     * The invariants that make a row a product at all (ADR 0017: the domain
     * refuses bad values, the advice layer maps the refusal to 422). They
     * live here — not in the service — because every write path funnels
     * through this constructor: form, import, later a purchase receipt.
     *
     * <p>{@code null} cost/minimumStock are the "born zero" defaults the
     * service fills in; price is required upstream (DTO), so a null price
     * is a programming error and fails loudly rather than silently.
     */
    public Product {
        if (price.signum() <= 0) {
            throw new InvalidProductException("Price must be greater than zero.");
        }
        if (cost.signum() < 0) {
            throw new InvalidProductException("Cost cannot be negative.");
        }
        if (minimumStock < 0) {
            throw new InvalidProductException("Minimum stock cannot be negative.");
        }
    }

    /** The status flip (api-design: PUT /{resource}/{id}/status with {active}). */
    public Product withActive(boolean active) {
        return new Product(id, sku, name, description, price, cost,
                minimumStock, stock, categoryId, active);
    }
}
