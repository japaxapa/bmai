package com.example.bms.order;

import com.example.bms.shared.DocumentNumber;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of the orders list: what a table needs, without the lines. The
 * total is the stored one (the column the detail read derives from the lines
 * on every write), so listing a page stays one query.
 */
public record OrderSummary(
        long id,
        DocumentNumber number,
        long customerId,
        String customerName,
        OrderStatus status,
        BigDecimal total,
        Instant createdAt) {
}
