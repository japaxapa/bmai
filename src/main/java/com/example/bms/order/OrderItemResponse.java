package com.example.bms.order;

import java.math.BigDecimal;

/** What the UI renders for one order line. */
public record OrderItemResponse(
        long id,
        long productId,
        String sku,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {
}
