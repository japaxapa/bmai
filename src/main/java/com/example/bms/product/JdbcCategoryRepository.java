package com.example.bms.product;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * The SQL half of the category port. Testcontainers PostgreSQL is the only
 * database these queries are ever run against (testing-strategy: never H2 —
 * the unique index has to be the real one).
 */
@Repository
public class JdbcCategoryRepository implements CategoryRepository {

    /** One mapping, used by every query: the column set never drifts. */
    private static final RowMapper<Category> CATEGORY_ROW =
            (rs, rowNum) -> new Category(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getBoolean("active"));

    private final JdbcTemplate jdbc;

    public JdbcCategoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Category> findAll(int page, int size) {
        // lower(name) in ORDER BY matches the V3 index's notion of a name, so
        // the list orders the same way uniqueness compares.
        return jdbc.query(
                "select id, name, active from categories "
                        + "order by lower(name), id limit ? offset ?",
                CATEGORY_ROW,
                size, (long) page * size);
    }

    @Override
    public long count() {
        return jdbc.queryForObject("select count(*) from categories", Long.class);
    }

    @Override
    public Optional<Category> findById(long id) {
        return jdbc.query(
                        "select id, name, active from categories where id = ?",
                        CATEGORY_ROW, id)
                .stream().findFirst();
    }

    @Override
    public Category save(Category category) {
        // Insert for a new row, update for one we loaded: the same port call
        // serves create, rename and the status flip. A brand-new row has no
        // id yet (the identity column decides it), a loaded row keeps its own.
        if (category.id() == 0) {
            var id = jdbc.queryForObject(
                    "insert into categories (name, active) values (?, ?) returning id",
                    Long.class,
                    category.name(), category.active());
            return category.withId(id);
        }
        jdbc.update(
                "update categories set name = ?, active = ? where id = ?",
                category.name(), category.active(), category.id());
        return category;
    }
}
