package com.example.bms.supplier;

import java.util.List;
import java.util.Optional;

/**
 * Where suppliers are read and written — a port over the {@code suppliers}
 * table, the same shape the category and product boundaries have: the service
 * logic can be exercised without a database (the HTTP slice stubs exactly this
 * boundary) and the SQL lives behind one class.
 */
public interface SupplierRepository {

    /**
     * One page of suppliers in display order, optionally narrowed by the
     * search term (api-design: suppliers carry search/filter/pagination;
     * {@code null} or blank means no filter).
     */
    List<Supplier> findAll(int page, int size, String search);

    /** Rows matching the search term — the count behind the page envelope. */
    long count(String search);

    /** The row with that id, empty when it does not exist (→ 404). */
    Optional<Supplier> findById(long id);

    /** Stores the row and answers it back with the id the database gave it. */
    Supplier save(Supplier supplier);
}
