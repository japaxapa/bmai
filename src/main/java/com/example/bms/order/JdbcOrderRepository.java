package com.example.bms.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * The SQL half of the order port, over V8's {@code orders} and
 * {@code order_items}. Testcontainers PostgreSQL is the only database these
 * queries are ever run against (testing-strategy: never H2 — the enum, the
 * CHECKs and the unique indexes have to be the real ones).
 *
 * <p>Two shapes: the list reads one joined row per order (a table needs no
 * lines), the detail reads the header then its lines. Writes go through
 * {@link #save}: a new draft asks the sequence for its number and receives
 * its identity, an edit replaces the line set — the body of a PUT is the
 * whole draft, so there is no per-line update path to get out of sync.
 */
@Repository
public class JdbcOrderRepository implements OrderRepository {

    /** The columns the list and the detail header both name, in one place. */
    private static final String SUMMARY_COLUMNS =
            "o.id, o.number, o.customer_id, c.name as customer_name, "
                    + "o.status, o.total, o.created_at";

    private static final RowMapper<OrderSummary> SUMMARY_ROW = (rs, rowNum) ->
            new OrderSummary(
                    rs.getLong("id"),
                    rs.getString("number"),
                    rs.getLong("customer_id"),
                    rs.getString("customer_name"),
                    OrderStatus.valueOf(rs.getString("status")),
                    rs.getBigDecimal("total"),
                    rs.getObject("created_at", OffsetDateTime.class).toInstant());

    private static final RowMapper<OrderItem> ITEM_ROW = (rs, rowNum) ->
            new OrderItem(
                    rs.getLong("id"),
                    rs.getLong("product_id"),
                    rs.getString("sku"),
                    rs.getString("product_name"),
                    rs.getInt("quantity"),
                    rs.getBigDecimal("unit_price"),
                    rs.getBigDecimal("subtotal"));

    private final JdbcTemplate jdbc;

    public JdbcOrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OrderSummary> findAll(int page, int size) {
        // Newest first: the draft a person just saved is the row they look
        // for, and the id breaks same-timestamp ties deterministically.
        return jdbc.query(
                "select " + SUMMARY_COLUMNS + " from orders o "
                        + "join customers c on c.id = o.customer_id "
                        + "order by o.created_at desc, o.id desc limit ? offset ?",
                SUMMARY_ROW, size, (long) page * size);
    }

    @Override
    public long count() {
        return jdbc.queryForObject("select count(*) from orders", Long.class);
    }

    @Override
    public Optional<Order> findById(long id) {
        var header = jdbc.query(
                        "select " + SUMMARY_COLUMNS + " from orders o "
                                + "join customers c on c.id = o.customer_id "
                                + "where o.id = ?",
                        SUMMARY_ROW, id)
                .stream().findFirst();
        if (header.isEmpty()) {
            return Optional.empty();
        }
        var row = header.get();
        var items = jdbc.query(
                "select i.id, i.product_id, p.sku, p.name as product_name, "
                        + "i.quantity, i.unit_price, i.subtotal "
                        + "from order_items i join products p on p.id = i.product_id "
                        + "where i.order_id = ? order by i.id",
                ITEM_ROW, id);
        return Optional.of(new Order(row.id(), row.number(), row.customerId(),
                row.customerName(), row.status(), items, row.total(), row.createdAt()));
    }

    @Override
    public Order save(Order order) {
        return order.id() == 0 ? insert(order) : update(order);
    }

    private Order insert(Order order) {
        var sequence = jdbc.queryForObject(
                "select nextval('orders_number_seq')", Long.class);
        var number = "ORD-%d-%04d".formatted(
                LocalDate.now(ZoneOffset.UTC).getYear(), sequence);
        var identity = jdbc.queryForObject("""
                        insert into orders (number, customer_id, status, total)
                        values (?, ?, cast(? as order_status), ?)
                        returning id, created_at
                        """,
                (rs, rowNum) -> new Identity(
                        rs.getLong("id"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()),
                number, order.customerId(), order.status().name(), order.total());
        var stored = order.withIdentity(identity.id(), number, identity.createdAt());
        return stored.withItems(insertItems(stored.id(), stored.items()));
    }

    private Order update(Order order) {
        jdbc.update("""
                        update orders
                        set customer_id = ?, total = ?, updated_at = now()
                        where id = ?
                        """,
                order.customerId(), order.total(), order.id());
        // A draft's line set is replaced wholesale: the request carries the
        // complete order, so a line left out of the body is a removal.
        jdbc.update("delete from order_items where order_id = ?", order.id());
        return order.withItems(insertItems(order.id(), order.items()));
    }

    private List<OrderItem> insertItems(long orderId, List<OrderItem> items) {
        return items.stream().map(item -> {
            var id = jdbc.queryForObject("""
                            insert into order_items
                                (order_id, product_id, quantity, unit_price, subtotal)
                            values (?, ?, ?, ?, ?)
                            returning id
                            """,
                    Long.class,
                    orderId, item.productId(), item.quantity(),
                    item.unitPrice(), item.subtotal());
            return item.withId(id);
        }).toList();
    }

    /** The identity the database assigned on insert. */
    private record Identity(long id, java.time.Instant createdAt) {
    }
}
