package com.example.bms.product;

import java.util.List;
import java.util.Optional;

/**
 * Where products are read and written — a port over the {@code products}
 * table, the same shape the category and user boundaries have: the service
 * logic can be exercised without a database (the HTTP slice stubs exactly
 * this boundary) and the SQL lives behind one class.
 */
public interface ProductRepository {

    /**
     * One page of products in display order, optionally narrowed by the
     * search term (api-design: ILIKE on name and sku; {@code null} or blank
     * means no filter).
     */
    List<ProductWithCategory> findAll(int page, int size, String search);

    /** Rows matching the search term — the count behind the page envelope. */
    long count(String search);

    /** The row with that id, empty when it does not exist (→ 404). */
    Optional<ProductWithCategory> findById(long id);

    /** Stores the row and answers it back with the id the database gave it. */
    Product save(Product product);
}
