package com.example.bms.product;

import com.example.bms.shared.LikeTerm;
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
                    + "p.minimum_stock, p.stock, p.category_id, p.supplier_id, p.active";

    /**
     * The live-search predicate, in one place — {@link #findAll} and
     * {@link #count} must filter identically forever: the envelope's
     * {@code items} come from the first, its {@code total}/{@code
     * totalPages} from the second, so a predicate edited in one and not the
     * other makes the pager lie (N rows listed under a total that counted a
     * different set). Growing the search to another column happens here,
     * once.
     */
    private static final String SEARCH_PREDICATE =
            "p.sku ilike ? escape '\\' or p.name ilike ? escape '\\'";

    /**
     * One mapping, used by every read: the join's category name rides along,
     * and the supplier's does too (left join — a product may have none, and
     * an inner join would simply make it invisible in the list).
     */
    private static final RowMapper<ProductRow> PRODUCT_ROW = (rs, rowNum) ->
            new ProductRow(
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
                            (Long) rs.getObject("supplier_id"),
                            rs.getBoolean("active")),
                    rs.getString("category_name"),
                    rs.getString("supplier_name"));

    private final JdbcTemplate jdbc;

    public JdbcProductRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ProductRow> findAll(int page, int size, String search) {
        var term = LikeTerm.of(search);
        // lower(p.sku) in ORDER BY matches the V4 index's notion of a SKU,
        // so the list orders the same way uniqueness compares.
        var sql = "select " + COLUMNS + ", c.name as category_name, s.name as supplier_name "
                + "from products p "
                + "join categories c on c.id = p.category_id "
                + "left join suppliers s on s.id = p.supplier_id "
                + (term == null
                        ? ""
                        : "where " + SEARCH_PREDICATE + " ")
                + "order by lower(p.sku), p.id limit ? offset ?";
        if (term == null) {
            return jdbc.query(sql, PRODUCT_ROW, size, (long) page * size);
        }
        return jdbc.query(sql, PRODUCT_ROW, term, term, size, (long) page * size);
    }

    @Override
    public long count(String search) {
        var term = LikeTerm.of(search);
        // Same alias as findAll's, so SEARCH_PREDICATE reads identically
        // here; the categories join is not needed for counting.
        if (term == null) {
            return jdbc.queryForObject("select count(*) from products", Long.class);
        }
        return jdbc.queryForObject(
                "select count(*) from products p where " + SEARCH_PREDICATE,
                Long.class, term, term);
    }

    @Override
    public Optional<ProductRow> findById(long id) {
        return jdbc.query(
                        "select " + COLUMNS + ", c.name as category_name, s.name as supplier_name "
                                + "from products p "
                                + "join categories c on c.id = p.category_id "
                                + "left join suppliers s on s.id = p.supplier_id "
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
                            + "minimum_stock, stock, category_id, supplier_id, active) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
                    Long.class,
                    product.sku(), product.name(), product.description(),
                    product.price(), product.cost(), product.minimumStock(),
                    product.stock(), product.categoryId(), product.supplierId(),
                    product.active());
            return product.withId(id);
        }
        jdbc.update(
                "update products set sku = ?, name = ?, description = ?, price = ?, cost = ?, "
                        + "minimum_stock = ?, stock = ?, category_id = ?, supplier_id = ?, active = ? "
                        + "where id = ?",
                product.sku(), product.name(), product.description(),
                product.price(), product.cost(), product.minimumStock(),
                product.stock(), product.categoryId(), product.supplierId(),
                product.active(), product.id());
        return product;
    }
}
