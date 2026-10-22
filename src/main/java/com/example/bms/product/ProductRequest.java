package com.example.bms.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * The write DTO create and edit share — one record holds the field
 * rules, so create cannot drift from edit. {@code description} is the
 * only optional column; cost and minimum stock are born zero when
 * omitted, and the category link is required (a product without one is
 * not a product — requirements: "Category: required on products"). The
 * business ranges (price > 0, cost ≥ 0, minimum stock ≥ 0) are the
 * domain's own refusals (422), not shape rules — they live on the
 * entity, not here.
 *
 * <p>It lives in the product package rather than nested in the
 * controller because it travels the seam: the controller binds it from
 * HTTP ({@code @Valid} enforces the annotations below there) and hands
 * it to the service as one typed value instead of seven loose
 * primitives (review S6 — a silent reorder of same-typed primitives
 * compiles across a 7-arg call).
 *
 * <p>{@code supplierId} is optional (ticket 7): a product may be listed
 * before anyone has decided who supplies it, and "no supplier" is an
 * absent id rather than a sentinel number the UI would have to know to
 * filter out.
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
        Long categoryId,
        Long supplierId) {
}
