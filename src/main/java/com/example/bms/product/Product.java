package com.example.bms.product;

import com.example.bms.shared.CanonicalForm;

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
        Long supplierId,
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
     *
     * <p>Money is {@code numeric(12,2)} (database-design), so a value finer
     * than cents is not one this domain can hold. Without the guard the
     * database would <em>round</em> it — 0.001 → 0.00 — turning a sub-cent
     * price into an unhandled CHECK violation (a 500, not this 422) and a
     * sub-cent cost into money that quietly changed value on the way in.
     *
     * <p>The SKU is <em>canonicalized</em>, not guarded (GLOSSARY "Canonical
     * form"): trimmed and lowercased, so the unique index compares one
     * spelling per identifier — "CAFE-1", "cafe-1" and " CAFE-1 " are the
     * same SKU here, exactly as the index sees them. The fold itself is
     * {@link CanonicalForm#lowercaseTrimmed} — the single home that also
     * argues why it is {@code Locale.ROOT} and never the JVM default
     * locale. The name is trimmed only: it is display data, and "Padaria"
     * must stay "Padaria".
     */
    public Product {
        sku = CanonicalForm.lowercaseTrimmed(sku);
        name = name.trim();
        if (price.signum() <= 0) {
            throw new InvalidProductException("Price must be greater than zero.");
        }
        requireCents(price, "Price");
        if (cost.signum() < 0) {
            throw new InvalidProductException("Cost cannot be negative.");
        }
        requireCents(cost, "Cost");
        if (minimumStock < 0) {
            throw new InvalidProductException("Minimum stock cannot be negative.");
        }
    }

    /** At most two decimal places — the scale the money column can hold. */
    private static void requireCents(BigDecimal money, String field) {
        if (money.stripTrailingZeros().scale() > 2) {
            throw new InvalidProductException(field + " can have at most two decimal places.");
        }
    }

    /** The status flip (api-design: PUT /{resource}/{id}/status with {active}). */
    public Product withActive(boolean active) {
        return new Product(id, sku, name, description, price, cost,
                minimumStock, stock, categoryId, supplierId, active);
    }

    /**
     * The insert path: same row, now carrying the identity column the
     * database assigned. Without this the repository would re-list all ten
     * fields by hand — a clone list that must silently stay in agreement
     * with the record's own shape.
     */
    public Product withId(long id) {
        return new Product(id, sku, name, description, price, cost,
                minimumStock, stock, categoryId, supplierId, active);
    }
}
