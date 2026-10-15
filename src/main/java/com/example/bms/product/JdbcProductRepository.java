package com.example.bms.product;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * The SQL half of the product port. Testcontainers PostgreSQL is the only
 * database these queries are ever run against (testing-strategy: never H2 —
 * the functional unique index and the ILIKE have to be the real ones).
 */
@Repository
public class JdbcProductRepository implements ProductRepository {

    /** The column set every read names, in one place — never drifts. */
    private static final String COLUMNS =
            "p.id, p.sku, p.name, p.description, p.price, p.cost, "
                    + "p.minimum_stock, p.stock, p.category_id, p.active";

    /** One mapping, used by every read: the join's category name rides along. */
    private static final RowMapper<ProductWithCategory> PRODUCT_ROW = (rs, rowNum) ->
            new ProductWithCategory(
                    new Product(
                            rs.getLong("id"),
                            rs.getString("sku"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getBigDecimal("price"),
                            rs.getBigDecimal("cost"),
                            rs.getInt("minimum_stock"),
                            rs.getInt("stock"),
                            rs.getLong("category_id"),
                            rs.getBoolean("active")),
                    rs.getString("category_name"));

    private final JdbcTemplate jdbc;

    public JdbcProductRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ProductWithCategory> findAll(int page, int size, String search) {
        var term = likeTerm(search);
        // lower(p.sku) in ORDER BY matches the V4 index's notion of a SKU,
        // so the list orders the same way uniqueness compares.
        var sql = "select " + COLUMNS + ", c.name as category_name "
                + "from products p join categories c on c.id = p.category_id "
                + (term == null
                        ? ""
                        : "where p.sku ilike ? escape '\\' or p.name ilike ? escape '\\' ")
                + "order by lower(p.sku), p.id limit ? offset ?";
        if (term == null) {
            return jdbc.query(sql, PRODUCT_ROW, size, (long) page * size);
        }
        return jdbc.query(sql, PRODUCT_ROW, term, term, size, (long) page * size);
    }

    @Override
    public long count(String search) {
        var term = likeTerm(search);
        if (term == null) {
            return jdbc.queryForObject("select count(*) from products", Long.class);
        }
        return jdbc.queryForObject(
                "select count(*) from products "
                        + "where sku ilike ? escape '\\' or name ilike ? escape '\\'",
                Long.class, term, term);
    }

    @Override
    public Optional<ProductWithCategory> findById(long id) {
        return jdbc.query(
                        "select " + COLUMNS + ", c.name as category_name "
                                + "from products p join categories c on c.id = p.category_id "
                                + "where p.id = ?",
                        PRODUCT_ROW, id)
                .stream().findFirst();
    }

    @Override
    public Product save(Product product) {
        // Insert for a new row, update for one we loaded: the same port call
        // serves create, edit and the status flip. A brand-new row has no id
        // yet (the identity column decides it); a loaded row keeps its own —
        // including the stock mirror, which this class only ever writes as
        // part of the whole row (ADR 0006: movements move stock, later).
        if (product.id() == 0) {
            var id = jdbc.queryForObject(
                    "insert into products (sku, name, description, price, cost, "
                            + "minimum_stock, stock, category_id, active) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
                    Long.class,
                    product.sku(), product.name(), product.description(),
                    product.price(), product.cost(), product.minimumStock(),
                    product.stock(), product.categoryId(), product.active());
            return new Product(id, product.sku(), product.name(), product.description(),
                    product.price(), product.cost(), product.minimumStock(),
                    product.stock(), product.categoryId(), product.active());
        }
        jdbc.update(
                "update products set sku = ?, name = ?, description = ?, price = ?, cost = ?, "
                        + "minimum_stock = ?, stock = ?, category_id = ?, active = ? where id = ?",
                product.sku(), product.name(), product.description(),
                product.price(), product.cost(), product.minimumStock(),
                product.stock(), product.categoryId(), product.active(), product.id());
        return product;
    }

    /**
     * The ILIKE term for a live search: wildcards the user typed are data,
     * not syntax — '%' in a search box must find a literal '%', not every
     * row. Blank (or absent) means no filter.
     */
    private static String likeTerm(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }
}
