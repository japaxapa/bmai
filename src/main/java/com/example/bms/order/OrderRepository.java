package com.example.bms.order;

import java.util.List;
import java.util.Optional;

/**
 * Where sales orders are read and written — a port over {@code orders} and
 * {@code order_items}. The list answers summaries (no lines: a page of
 * orders is a table), the detail answers the aggregate with its items, and
 * {@link #save} is the one write path: insert for a new draft, update plus
 * line replacement for an existing one.
 */
public interface OrderRepository {

    /** One page of order summaries, newest first. */
    List<OrderSummary> findAll(int page, int size);

    /** Rows behind the page envelope. */
    long count();

    /** The aggregate with its lines, empty when the order does not exist (→ 404). */
    Optional<Order> findById(long id);

    /**
     * Stores the order. A new draft gets its identity, number
     * ({@code ORD-YYYY-NNNN}) and creation stamp from the database; an edit
     * replaces the line set and keeps everything else.
     */
    Order save(Order order);
}
