package com.example.bms.order;

import com.example.bms.customer.Customer;
import com.example.bms.customer.CustomerRepository;
import com.example.bms.product.CategoryRepository;
import com.example.bms.product.Product;
import com.example.bms.product.ProductRepository;
import com.example.bms.product.ProductRow;
import com.example.bms.shared.DocumentNumber;
import com.example.bms.shared.SecurityConfig;
import com.example.bms.supplier.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seam: the orders HTTP surface, observed through MockMvc exactly as curl
 * would see it. The database is stubbed at the repositories (the same seam
 * the customer slice uses); the real migrations, joins and constraints are
 * {@link OrderEndToEndTest}'s job.
 *
 * <p>The collaborators are the real services: an order reads its customer and
 * products through the other features' public services (architecture §1),
 * never their repositories, so the slice imports them and stubs the SQL
 * underneath.
 */
@WebMvcTest(controllers = OrderController.class)
@Import({SecurityConfig.class, OrderService.class,
        com.example.bms.customer.CustomerService.class,
        com.example.bms.product.ProductService.class,
        com.example.bms.supplier.SupplierService.class})
class OrderEndpointTest {

    @Autowired
    private MockMvc mvc;

    /**
     * The two products and one customer the draft tests draw from. Prices are
     * the known-good literals the totals are checked against: 2 × 12.50 =
     * 25.00, 3 × 4.10 = 12.30, total 37.30.
     */
    @TestConfiguration
    static class StubbedDatabase {

        @Bean
        CustomerRepository customerRepository() {
            var customer = new Customer(7, "Mercado Rio Doce ME", "11222333000181",
                    "contato@riodoce.com.br", 30, true);
            return new CustomerRepository() {
                @Override
                public List<Customer> findAll(int page, int size, String search) {
                    return List.of(customer);
                }

                @Override
                public long count(String search) {
                    return 1;
                }

                @Override
                public Optional<Customer> findById(long id) {
                    return id == customer.id() ? Optional.of(customer) : Optional.empty();
                }

                @Override
                public Customer save(Customer stored) {
                    throw new UnsupportedOperationException("not used by the order slice");
                }
            };
        }

        @Bean
        ProductRepository productRepository() {
            var coffee = new Product(11, "cafe-1", "Café Torrado 1kg", null,
                    new BigDecimal("12.50"), new BigDecimal("8.00"), 0, 10, 1, null, true);
            var sugar = new Product(12, "acucar-1", "Açúcar Refinado 1kg", null,
                    new BigDecimal("4.10"), new BigDecimal("2.50"), 0, 10, 1, null, true);
            return new ProductRepository() {
                @Override
                public List<ProductRow> findAll(int page, int size, String search) {
                    return List.of();
                }

                @Override
                public long count(String search) {
                    return 0;
                }

                @Override
                public Optional<ProductRow> findById(long id) {
                    if (id == coffee.id()) {
                        return Optional.of(new ProductRow(coffee, "Mercearia", null));
                    }
                    if (id == sugar.id()) {
                        return Optional.of(new ProductRow(sugar, "Mercearia", null));
                    }
                    return Optional.empty();
                }

                @Override
                public Product save(Product product) {
                    throw new UnsupportedOperationException("not used by the order slice");
                }
            };
        }

        @Bean
        CategoryRepository categoryRepository() {
            return new CategoryRepository() {
                @Override
                public List<com.example.bms.product.Category> findAll(int page, int size) {
                    throw new UnsupportedOperationException("orders never touch categories directly");
                }

                @Override
                public long count() {
                    throw new UnsupportedOperationException("orders never touch categories directly");
                }

                @Override
                public Optional<com.example.bms.product.Category> findById(long id) {
                    throw new UnsupportedOperationException("orders never touch categories directly");
                }

                @Override
                public com.example.bms.product.Category save(
                        com.example.bms.product.Category category) {
                    throw new UnsupportedOperationException("orders never touch categories directly");
                }
            };
        }

        @Bean
        SupplierRepository supplierRepository() {
            return new SupplierRepository() {
                @Override
                public List<com.example.bms.supplier.Supplier> findAll(int page, int size, String search) {
                    throw new UnsupportedOperationException("orders never touch suppliers directly");
                }

                @Override
                public long count(String search) {
                    throw new UnsupportedOperationException("orders never touch suppliers directly");
                }

                @Override
                public Optional<com.example.bms.supplier.Supplier> findById(long id) {
                    throw new UnsupportedOperationException("orders never touch suppliers directly");
                }

                @Override
                public com.example.bms.supplier.Supplier save(
                        com.example.bms.supplier.Supplier supplier) {
                    throw new UnsupportedOperationException("orders never touch suppliers directly");
                }
            };
        }

        /**
         * An in-memory orders table. The stub assigns the identity, number and
         * creation stamp the way V8 will, so the response shape — not the
         * number generator — is what the slice pins.
         */
        @Bean
        OrderRepository orderRepository() {
            var rows = new ConcurrentHashMap<Long, Order>();
            var ids = new AtomicLong(1);
            var createdAt = Instant.parse("2026-10-27T12:00:00Z");
            // Two stored rows with known statuses: one editable draft and one
            // confirmed order, so the edit tests never depend on what an
            // earlier test created.
            rows.put(50L, new Order(50, DocumentNumber.parse("ORD-2026-0050"), 7,
                    "Mercado Rio Doce ME",
                    OrderStatus.DRAFT, List.of(OrderItem.of(11, "cafe-1",
                            "Café Torrado 1kg", 2, new BigDecimal("12.50"))),
                    BigDecimal.ZERO, createdAt));
            rows.put(99L, new Order(99, DocumentNumber.parse("ORD-2026-0099"), 7,
                    "Mercado Rio Doce ME",
                    OrderStatus.CONFIRMED, List.of(OrderItem.of(11, "cafe-1",
                            "Café Torrado 1kg", 2, new BigDecimal("12.50"))),
                    BigDecimal.ZERO, createdAt));
            return new OrderRepository() {
                @Override
                public List<OrderSummary> findAll(int page, int size) {
                    return rows.values().stream()
                            .map(order -> new OrderSummary(order.id(), order.number(),
                                    order.customerId(), order.customerName(), order.status(),
                                    order.total(), order.createdAt()))
                            .skip((long) page * size)
                            .limit(size)
                            .toList();
                }

                @Override
                public long count() {
                    return rows.size();
                }

                @Override
                public Optional<Order> findById(long id) {
                    return Optional.ofNullable(rows.get(id));
                }

                @Override
                public Order save(Order order) {
                    var id = order.id() == 0 ? ids.getAndIncrement() : order.id();
                    var stored = order.id() == 0
                            ? order.withIdentity(id,
                                    DocumentNumber.parse("ORD-2026-000" + id),
                                    Instant.parse("2026-10-27T12:00:00Z"))
                            : order;
                    rows.put(id, stored);
                    return stored;
                }
            };
        }
    }

    /**
     * AC: "Draft sales order creation ... and totals shown in the UI." 201
     * with the stored draft: the server names it, marks it DRAFT, echoes the
     * customer and lines, and — the part the client must never compute —
     * prices and totals each line.
     *
     * <p>An EMPLOYEE posts it: business-rules §4 gives the sales clerk the
     * whole order flow through COMPLETED.
     */
    @Test
    void employeeCreateAnswers201WithTheDraftAndItsComputedTotals() throws Exception {
        mvc.perform(post("/api/orders").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":7,"items":[
                                  {"productId":11,"quantity":2},
                                  {"productId":12,"quantity":3}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.number", startsWith("ORD-")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.customerId").value(7))
                .andExpect(jsonPath("$.customerName").value("Mercado Rio Doce ME"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productId").value(11))
                .andExpect(jsonPath("$.items[0].sku").value("cafe-1"))
                .andExpect(jsonPath("$.items[0].productName").value("Café Torrado 1kg"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(12.50))
                .andExpect(jsonPath("$.items[0].subtotal").value(25.00))
                .andExpect(jsonPath("$.items[1].subtotal").value(12.30))
                .andExpect(jsonPath("$.total").value(37.30));
    }

    /**
     * AC: "Invalid quantities rejected with field-level errors." The answer is
     * a 400 (DTO shape, api-design errors table) whose {@code fieldErrors}
     * names the exact nested field, because that is what the form prints
     * under the box — a bare "Validation failed" gives it nowhere to go.
     * Zero is the shape a person produces by clearing the quantity box.
     */
    @Test
    void aZeroQuantityIsRefusedWithAFieldErrorNamingTheLine() throws Exception {
        mvc.perform(post("/api/orders").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":7,"items":[{"productId":11,"quantity":0}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors['items[0].quantity'][0]")
                        .value("Quantity must be greater than zero."));
    }

    /**
     * The same refusal for the other value a person can type: a negative
     * quantity. Named separately because the guard must be "greater than
     * zero", not "not zero".
     */
    @Test
    void aNegativeQuantityIsRefusedWithAFieldErrorNamingTheLine() throws Exception {
        mvc.perform(post("/api/orders").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":7,"items":[{"productId":12,"quantity":-3}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['items[0].quantity'][0]")
                        .value("Quantity must be greater than zero."));
    }

    /**
     * AC: "Editing a non-draft sales order is rejected." 409, not 422: the
     * order exists and its state conflicts with the request (api-design
     * errors table), and the sentence is the aggregate's own — the same one
     * the unit test pins without any HTTP in the way.
     */
    @Test
    void editingAConfirmedOrderIsRefusedAs409() throws Exception {
        mvc.perform(put("/api/orders/99").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":7,"items":[{"productId":12,"quantity":1}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail")
                        .value("Only DRAFT sales orders can be edited."));
    }

    /**
     * The editable half of the same rule, and the AC's "adds and removes
     * order items": PUT carries the complete item set, so replacing two
     * coffees with three sugars both removes a line and adds another, and
     * the server re-prices from the product (12.30, not the old 25.00). The
     * number a person reads is the draft's own and does not change.
     */
    @Test
    void editingADraftReplacesItsLinesAndKeepsTheNumber() throws Exception {
        mvc.perform(put("/api/orders/50").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":7,"items":[{"productId":12,"quantity":3}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.number").value("ORD-2026-0050"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(12))
                .andExpect(jsonPath("$.items[0].subtotal").value(12.30))
                .andExpect(jsonPath("$.total").value(12.30));
    }

    /**
     * api-design errors table: "404 | unknown id". A stale link must answer
     * the shared problem+json envelope — not a 500, and not an insert.
     */
    @Test
    void anUnknownOrderIsA404ProblemJson() throws Exception {
        mvc.perform(put("/api/orders/999").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":7,"items":[]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No order with id 999"));
    }

    /** The detail read: the draft with its lines, the shape the edit page seeds from. */
    @Test
    void theDetailAnswersTheDraftWithItsLines() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/orders/50").with(user("clerk").roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("ORD-2026-0050"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sku").value("cafe-1"))
                .andExpect(jsonPath("$.total").value(25.00));
    }

    /**
     * The orders table binds to the same paged envelope every list answers,
     * and reading stays open to any signed-in role (business-rules §4 gates
     * *writing* the order flow, never seeing the list).
     */
    @Test
    void anySignedInRoleMayListOrdersInAPagedEnvelope() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/orders").with(user("clerk").roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.size").exists())
                .andExpect(jsonPath("$.total").exists())
                .andExpect(jsonPath("$.totalPages").exists());
    }

    /**
     * A role outside the business-rules §4 write list is refused by the
     * service gate, with the sentence the frontend toast renders. The write
     * list is enumerated (ADMIN/MANAGER/EMPLOYEE), not "any signed-in role",
     * so a future fourth role stays out until the matrix says otherwise.
     */
    @Test
    void aRoleOutsideTheWriteListCannotCreateADraft() throws Exception {
        mvc.perform(post("/api/orders").with(user("viewer").roles("VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":7,"items":[]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
