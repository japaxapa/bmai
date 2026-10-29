package com.example.bms.order;

import com.example.bms.shared.DocumentNumber;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A sales order (GLOSSARY "Sales order") as the draft slice sees it: the
 * customer, the lines and the total the backend computed. Immutable, like
 * {@code Customer} and {@code Product} — an edit produces a new instance, so
 * the service never hands out a value somebody can mutate behind its back.
 *
 * <p>The total is derived from the lines rather than carried as an input
 * (business-rules §1: "Total — sum of order-item subtotals, computed by the
 * backend, never trusted from the client"). Rows read for the list use
 * {@link OrderSummary}, where the stored total is the only money the row
 * needs.
 */
public record Order(
        long id,
        DocumentNumber number,
        long customerId,
        String customerName,
        OrderStatus status,
        List<OrderItem> items,
        BigDecimal total,
        Instant createdAt) {

    public Order {
        items = List.copyOf(items);
        total = items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
    }

    /** A brand-new draft: no identity or number until the database assigns them. */
    public static Order draft(long customerId, String customerName, List<OrderItem> items) {
        return new Order(0, null, customerId, customerName, OrderStatus.DRAFT, items,
                BigDecimal.ZERO, null);
    }

    /**
     * The insert path: same contents, now carrying the identity, number and
     * creation stamp the database assigned — so the repository never
     * re-lists the aggregate's fields by hand.
     */
    public Order withIdentity(long id, DocumentNumber number, Instant createdAt) {
        return new Order(id, number, customerId, customerName, status, items,
                BigDecimal.ZERO, createdAt);
    }

    /**
     * The save path's answer: the same order, now carrying the line ids the
     * database assigned. The total re-derives from them (it always does), so
     * the response the caller sees and the row it stored cannot disagree.
     */
    public Order withItems(List<OrderItem> items) {
        return new Order(id, number, customerId, customerName, status, items,
                BigDecimal.ZERO, createdAt);
    }

    /**
     * The edit path: same row, new contents. Only a DRAFT accepts it
     * (business-rules §1), and everything the form does not write — the id,
     * the number, the status and the creation stamp — rides through
     * untouched.
     */
    public Order withDraft(long customerId, String customerName, List<OrderItem> items) {
        if (status != OrderStatus.DRAFT) {
            throw new OrderNotEditableException();
        }
        return new Order(id, number, customerId, customerName, status, items,
                BigDecimal.ZERO, createdAt);
    }
}
