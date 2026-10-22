package com.example.bms.customer;

import java.util.List;

/**
 * Where customers are read and written — a port over the {@code customers}
 * table, the same shape the supplier boundary has: the service logic can be
 * exercised without a database (the HTTP slice stubs exactly this boundary)
 * and the SQL lives behind one class.
 */
public interface CustomerRepository {

    /**
     * One page of customers in display order, optionally narrowed by the
     * search term (api-design: customers carry search/filter/pagination;
     * {@code null} or blank means no filter).
     */
    List<Customer> findAll(int page, int size, String search);

    /** Rows matching the search term — the count behind the page envelope. */
    long count(String search);

    /** Stores the row and answers it back with the id the database gave it. */
    Customer save(Customer customer);
}
