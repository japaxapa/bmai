package com.example.bms.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The write DTO create and edit share — PUT replaces the whole draft, so
 * adding, removing and re-quantifying lines are the same verb and the body
 * always describes the complete order.
 *
 * <p>Shape only: the customer and product ids must be present and each
 * quantity positive ({@code @Positive} answers a 400 naming
 * {@code items[i].quantity}, the field-level error the UI prints under the
 * box). Whether those ids exist, are active, or repeat is the domain's
 * decision (404/422 after the load).
 */
public record OrderRequest(
        @NotNull(message = "Customer is required.")
        Long customerId,

        @NotNull(message = "Items are required.")
        List<@NotNull(message = "An item is required.") @Valid OrderItemRequest> items) {
}
