package com.example.bms.order;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of the orders table — the same shape a future detail page links from. */
public record OrderSummaryResponse(
        long id,
        String number,
        long customerId,
        String customerName,
        OrderStatus status,
        BigDecimal total,
        Instant createdAt) {
}
