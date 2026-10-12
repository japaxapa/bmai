package com.example.bms.product;

import java.util.List;
import java.util.Optional;

/**
 * Where categories are read and written — a port over the {@code categories}
 * table, the same shape auth gives {@code UserRepository}: the service logic
 * can be exercised without a database (the HTTP slice stubs exactly this
 * boundary) and the SQL lives behind one class.
 */
public interface CategoryRepository {

    /**
     * One page of categories in display order — alphabetical, compared the
     * way the unique index compares a name (case-insensitively).
     */
    List<Category> findAll(int page, int size);

    long count();

    /** The row with that id, empty when it does not exist (→ 404). */
    Optional<Category> findById(long id);

    /** Stores the row and answers it back with the id the database gave it. */
    Category save(Category category);
}
