package com.example.bms.customer;

import com.example.bms.shared.LikeTerm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * The SQL half of the customer port. Testcontainers PostgreSQL is the only
 * database these queries are ever run against (testing-strategy: never H2 —
 * the unique {@code document} index and the ILIKE have to be the real ones).
 */
@Repository
public class JdbcCustomerRepository implements CustomerRepository {

    /** The column set every read names, in one place — never drifts. */
    private static final String COLUMNS =
            "c.id, c.name, c.document, c.email, c.payment_terms_days, c.active";

    /**
     * The live-search predicate, in one place — {@link #findAll} and
     * {@link #count} must filter identically forever: the envelope's
     * {@code items} come from the first, its {@code total}/{@code
     * totalPages} from the second, so a predicate edited in one and not the
     * other makes the pager lie.
     *
     * <p>Two columns, and the second one only makes sense because the stored
     * form is canonical: a person searching has the punctuated CPF/CNPJ in
     * front of them, but {@code c.document} holds digits. The term is
     * normalized to digits before it is used *only* for this column — the
     * name half must still match what was typed, digits stripped out and all.
     */
    private static final String SEARCH_PREDICATE =
            "c.name ilike ? escape '\\' or c.document ilike ? escape '\\'";

    /** One mapping, used by every read. */
    private static final RowMapper<Customer> CUSTOMER_ROW = (rs, rowNum) ->
            new Customer(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("document"),
                    rs.getString("email"),
                    rs.getInt("payment_terms_days"),
                    rs.getBoolean("active"));

    private final JdbcTemplate jdbc;

    public JdbcCustomerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Customer> findAll(int page, int size, String search) {
        var term = LikeTerm.of(search);
        var documentTerm = LikeTerm.of(digitsOf(search));
        // Display order by name: customers have no natural sequence a person
        // would recognise (unlike a SKU), so the list is alphabetical and the
        // id only breaks ties.
        var sql = "select " + COLUMNS + " from customers c "
                + (term == null
                        ? ""
                        : "where " + SEARCH_PREDICATE + " ")
                + "order by lower(c.name), c.id limit ? offset ?";
        if (term == null) {
            return jdbc.query(sql, CUSTOMER_ROW, size, (long) page * size);
        }
        return jdbc.query(sql, CUSTOMER_ROW, term, documentTerm,
                size, (long) page * size);
    }

    @Override
    public long count(String search) {
        var term = LikeTerm.of(search);
        var documentTerm = LikeTerm.of(digitsOf(search));
        // Same aliases as findAll's, so SEARCH_PREDICATE reads identically.
        if (term == null) {
            return jdbc.queryForObject("select count(*) from customers", Long.class);
        }
        return jdbc.queryForObject(
                "select count(*) from customers c where " + SEARCH_PREDICATE,
                Long.class, term, documentTerm);
    }

    @Override
    public Optional<Customer> findById(long id) {
        return jdbc.query(
                        "select " + COLUMNS + " from customers c where c.id = ?",
                        CUSTOMER_ROW, id)
                .stream().findFirst();
    }

    @Override
    public Customer save(Customer customer) {
        // Insert for a new row, update for one we loaded: the same port call
        // serves create, edit and the status flip — the shape
        // JdbcSupplierRepository settles.
        if (customer.id() == 0) {
            var id = jdbc.queryForObject(
                    "insert into customers (name, document, email, payment_terms_days, active) "
                            + "values (?, ?, ?, ?, ?) returning id",
                    Long.class,
                    customer.name(), customer.document(), customer.email(),
                    customer.paymentTermsDays(), customer.active());
            return customer.withId(id);
        }
        jdbc.update(
                "update customers set name = ?, document = ?, email = ?, "
                        + "payment_terms_days = ?, active = ? where id = ?",
                customer.name(), customer.document(), customer.email(),
                customer.paymentTermsDays(), customer.active(), customer.id());
        return customer;
    }

    /**
     * The entity's own digit-stripping, delegated
     * ({@link Customer#canonicalDocument}), so a search for
     * {@code 11.222.333/0001-81} finds the row stored as
     * {@code 11222333000181} by construction. Never null —
     * {@code digitsOf(null)} is an empty string, and {@link LikeTerm#of}
     * turns that into "no filter" just as it does for a blank name term.
     */
    private static String digitsOf(String search) {
        return search == null ? "" : Customer.canonicalDocument(search);
    }
}
