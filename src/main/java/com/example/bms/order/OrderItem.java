package com.example.bms.order;

import java.math.BigDecimal;

/**
 * One product line on a sales order (GLOSSARY "Order item"). The unit price
 * is the product's current price while the order is a DRAFT — a draft is a
 * scratchpad and is re-priced on every save; CONFIRM locks it (ticket #10,
 * GLOSSARY "Price").
 *
 * <p>{@code subtotal} is not trusted from the caller: the canonical
 * constructor derives it from quantity × unit price, so an item read back
 * from the database and one built by the service agree by construction
 * (business-rules §1 / GLOSSARY "Total": the backend computes money, never
 * the client). Money is {@code numeric(12,2)}, and both factors are exact at
 * that scale, so the product is exact too.
 */
public record OrderItem(
        long id,
        long productId,
        String sku,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {

    public OrderItem {
        if (quantity <= 0) {
            throw new InvalidOrderException("Quantity must be greater than zero.");
        }
        subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2);
    }

    /** The write path's line: no identity yet, subtotal derived here. */
    public static OrderItem of(long productId, String sku, String productName,
                               int quantity, BigDecimal unitPrice) {
        return new OrderItem(0, productId, sku, productName, quantity, unitPrice,
                BigDecimal.ZERO);
    }

    /** The save path's answer: the same line, now carrying the identity. */
    public OrderItem withId(long id) {
        return new OrderItem(id, productId, sku, productName, quantity, unitPrice,
                subtotal);
    }
}
