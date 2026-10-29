package com.example.bms.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * One line of the draft body. The quantity rule is shape (a positive whole
 * number) and is answered as a 400; the unit price is deliberately absent —
 * it comes from the product, never from the client (business-rules §1).
 */
public record OrderItemRequest(
        @NotNull(message = "Product is required.")
        Long productId,

        @Positive(message = "Quantity must be greater than zero.")
        int quantity) {
}
