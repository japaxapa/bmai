package com.example.bms.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The order detail: identity, status, customer and lines with the totals the
 * backend computed. The UI renders this answer rather than re-deriving money
 * from what it typed.
 */
public record OrderResponse(
        long id,
        String number,
        long customerId,
        String customerName,
        OrderStatus status,
        List<OrderItemResponse> items,
        BigDecimal total,
        Instant createdAt) {
}
