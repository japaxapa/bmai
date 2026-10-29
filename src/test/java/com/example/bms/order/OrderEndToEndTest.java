package com.example.bms.order;

import com.example.bms.auth.AuthCookie;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seam: the draft order journey over the real stack — V8's tables, the real
 * repository, the real customer/product reads through their services, and the
 * real filter chain — observed only over HTTP (plus a status flip by SQL where
 * ticket #10's verb does not exist yet).
 *
 * <p>What a stubbed slice cannot prove is here: the number the sequence
 * generates is unique and shaped, the totals survive the numeric(12,2)
 * round trip, the line set really is replaced in the database, and a refusal
 * leaves no half-written order behind.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class OrderEndToEndTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * The whole draft in one journey: create with two lines (server names it
     * and prices it), read it back, replace the lines (remove one, add
     * another, re-quantify), and find it on the list. Each step asserts the
     * previous answer, so a broken hand-off between write and read cannot
     * hide behind a later step.
     */
    @Test
    void adminCanCreateEditListAndReadADraft() {
        var cookie = adminCookie();
        var customerId = createCustomer(cookie, "Mercado Aurora Pedidos ME",
                "12.345.678/0001-95");
        var coffeeId = createProduct(cookie, "cafe-ord-1", "Café Torrado 1kg", "12.50");
        var sugarId = createProduct(cookie, "acucar-ord-1", "Açúcar Refinado 1kg", "4.10");

        var created = post("/api/orders", Map.of(
                "customerId", customerId,
                "items", List.of(
                        Map.of("productId", coffeeId, "quantity", 2),
                        Map.of("productId", sugarId, "quantity", 3))), cookie);

        assertThat(created.getStatusCode().value()).isEqualTo(201);
        var body = created.getBody();
        var id = ((Number) field(body, "$.id")).longValue();
        assertThat(field(body, "$.number")).asString()
                .as("ORD-YYYY-NNNN, generated server-side (api-design)")
                .matches("ORD-\\d{4}-\\d{4,}");
        assertThat(field(body, "$.status")).isEqualTo("DRAFT");
        assertThat(field(body, "$.customerId")).isEqualTo((int) customerId);
        assertThat(field(body, "$.customerName")).isEqualTo("Mercado Aurora Pedidos ME");
        // 2 × 12.50 = 25.00; 3 × 4.10 = 12.30; total 37.30.
        assertThat(amount(body, "$.items[0].subtotal")).isEqualByComparingTo("25.00");
        assertThat(amount(body, "$.items[1].subtotal")).isEqualByComparingTo("12.30");
        assertThat(amount(body, "$.total")).isEqualByComparingTo("37.30");

        var detail = get("/api/orders/" + id, cookie);
        assertThat(detail.getStatusCode().value()).isEqualTo(200);
        assertThat(field(detail.getBody(), "$.number")).isEqualTo(field(body, "$.number"));
        assertThat((List<?>) field(detail.getBody(), "$.items")).hasSize(2);
        assertThat(amount(detail.getBody(), "$.total")).isEqualByComparingTo("37.30");

        // PUT replaces the whole draft: coffee is removed, sugar re-quantified.
        var edited = put("/api/orders/" + id, Map.of(
                "customerId", customerId,
                "items", List.of(Map.of("productId", sugarId, "quantity", 3))), cookie);
        assertThat(edited.getStatusCode().value()).isEqualTo(200);
        assertThat(field(edited.getBody(), "$.number")).isEqualTo(field(body, "$.number"));
        assertThat((List<?>) field(edited.getBody(), "$.items")).hasSize(1);
        assertThat(field(edited.getBody(), "$.items[0].productId"))
                .isEqualTo((int) sugarId);
        assertThat(amount(edited.getBody(), "$.total")).isEqualByComparingTo("12.30");

        var listed = get("/api/orders", cookie);
        assertThat(listed.getStatusCode().value()).isEqualTo(200);
        assertThat(numbersIn(field(listed.getBody(), "$.items")))
                .as("the created draft is visible on the table")
                .contains((String) field(body, "$.number"));
    }

    /**
     * AC: "Editing a non-draft sales order is rejected." Ticket #10 owns the
     * confirm verb, so the status is flipped by SQL — the point here is not
     * how an order became CONFIRMED but that the edit path refuses it and
     * leaves the stored lines exactly as they were.
     */
    @Test
    void editingAConfirmedOrderIsRejectedAndChangesNothing() {
        var cookie = adminCookie();
        var customerId = createCustomer(cookie, "Mercado Recusado ME",
                cnpj("121212120001"));
        var coffeeId = createProduct(cookie, "cafe-ord-2", "Café Torrado 1kg", "12.50");
        var sugarId = createProduct(cookie, "acucar-ord-2", "Açúcar Refinado 1kg", "4.10");
        var created = post("/api/orders", Map.of(
                "customerId", customerId,
                "items", List.of(Map.of("productId", coffeeId, "quantity", 2))), cookie);
        var id = ((Number) field(created.getBody(), "$.id")).longValue();
        jdbc.update("update orders set status = 'CONFIRMED' where id = ?", id);

        var refused = put("/api/orders/" + id, Map.of(
                "customerId", customerId,
                "items", List.of(Map.of("productId", sugarId, "quantity", 1))), cookie);

        assertThat(refused.getStatusCode().value()).isEqualTo(409);
        assertThat(refused.getHeaders().getContentType().toString())
                .as("RFC 9457 body, same shape as every other refusal")
                .contains("problem+json");
        assertThat(field(refused.getBody(), "$.status")).isEqualTo(409);
        assertThat(field(refused.getBody(), "$.detail"))
                .isEqualTo("Only DRAFT sales orders can be edited.");

        var detail = get("/api/orders/" + id, cookie);
        assertThat(field(detail.getBody(), "$.status")).isEqualTo("CONFIRMED");
        assertThat((List<?>) field(detail.getBody(), "$.items"))
                .as("the refused edit stored nothing")
                .hasSize(1);
        assertThat(field(detail.getBody(), "$.items[0].productId"))
                .isEqualTo((int) coffeeId);
        assertThat(amount(detail.getBody(), "$.total"))
                .isEqualByComparingTo("25.00");
    }

    /**
     * AC: "Invalid quantities rejected with field-level errors." The answer
     * names the nested field the form prints under, and the refusal runs
     * before anything is written — the orders count is the independent check.
     */
    @Test
    void anInvalidQuantityIsAFieldErrorAndStoresNothing() {
        var cookie = adminCookie();
        var customerId = createCustomer(cookie, "Mercado Quantidade ME",
                cnpj("131313130001"));
        var coffeeId = createProduct(cookie, "cafe-ord-3", "Café Torrado 1kg", "12.50");
        var before = jdbc.queryForObject("select count(*) from orders", Long.class);

        var refused = post("/api/orders", Map.of(
                "customerId", customerId,
                "items", List.of(Map.of("productId", coffeeId, "quantity", 0))), cookie);

        assertThat(refused.getStatusCode().value()).isEqualTo(400);
        assertThat(field(refused.getBody(), "$.fieldErrors['items[0].quantity'][0]"))
                .isEqualTo("Quantity must be greater than zero.");
        assertThat(jdbc.queryForObject("select count(*) from orders", Long.class))
                .as("nothing was stored behind the refusal")
                .isEqualTo(before);
    }

    /**
     * api-design errors table: an unknown id is a 404 from the feature that
     * owns it — the customer's or product's own sentence — never a
     * foreign-key 500 and never a stored row.
     */
    @Test
    void unknownCustomerOrProductIsA404() {
        var cookie = adminCookie();
        var customerId = createCustomer(cookie, "Mercado Desconhecido ME",
                cnpj("141414140001"));
        var coffeeId = createProduct(cookie, "cafe-ord-4", "Café Torrado 1kg", "12.50");

        var noCustomer = post("/api/orders", Map.of(
                "customerId", 999_999,
                "items", List.of(Map.of("productId", coffeeId, "quantity", 1))), cookie);
        assertThat(noCustomer.getStatusCode().value()).isEqualTo(404);
        assertThat(field(noCustomer.getBody(), "$.detail"))
                .isEqualTo("No customer with id 999999");

        var noProduct = post("/api/orders", Map.of(
                "customerId", customerId,
                "items", List.of(Map.of("productId", 999_999, "quantity", 1))), cookie);
        assertThat(noProduct.getStatusCode().value()).isEqualTo(404);
        assertThat(field(noProduct.getBody(), "$.detail"))
                .isEqualTo("No product with id 999999");
    }

    /**
     * business-rules §5: a deactivated party or product is blocked in all
     * *new* documents. The 422 names what is retired, so the form or toast
     * has a sentence to print.
     */
    @Test
    void deactivatedCustomerOrProductIsRefusedAs422() {
        var cookie = adminCookie();
        var customerId = createCustomer(cookie, "Mercado Aposentado ME",
                cnpj("151515150001"));
        var coffeeId = createProduct(cookie, "cafe-ord-5", "Café Torrado 1kg", "12.50");

        var customerOff = put("/api/customers/" + customerId + "/status",
                Map.of("active", false), cookie);
        assertThat(customerOff.getStatusCode().value()).isEqualTo(200);
        var refusedCustomer = post("/api/orders", Map.of(
                "customerId", customerId,
                "items", List.of(Map.of("productId", coffeeId, "quantity", 1))), cookie);
        assertThat(refusedCustomer.getStatusCode().value()).isEqualTo(422);
        assertThat(field(refusedCustomer.getBody(), "$.detail"))
                .isEqualTo("Customer is deactivated.");

        var otherCustomer = createCustomer(cookie, "Mercado Ativo ME",
                cnpj("161616160001"));
        var productOff = put("/api/products/" + coffeeId + "/status",
                Map.of("active", false), cookie);
        assertThat(productOff.getStatusCode().value()).isEqualTo(200);
        var refusedProduct = post("/api/orders", Map.of(
                "customerId", otherCustomer,
                "items", List.of(Map.of("productId", coffeeId, "quantity", 1))), cookie);
        assertThat(refusedProduct.getStatusCode().value()).isEqualTo(422);
        assertThat(field(refusedProduct.getBody(), "$.detail"))
                .isEqualTo("Product cafe-ord-5 is deactivated.");
    }

    /**
     * One product, one line. Two lines for the same product would make every
     * later availability check ambiguous (which line reserves what), so the
     * request is refused as a business rule — and V8's unique
     * (order_id, product_id) index is the backstop underneath.
     */
    @Test
    void aProductListedTwiceIsRefusedAs422() {
        var cookie = adminCookie();
        var customerId = createCustomer(cookie, "Mercado Duplicado ME",
                cnpj("171717170001"));
        var coffeeId = createProduct(cookie, "cafe-ord-6", "Café Torrado 1kg", "12.50");

        var refused = post("/api/orders", Map.of(
                "customerId", customerId,
                "items", List.of(
                        Map.of("productId", coffeeId, "quantity", 1),
                        Map.of("productId", coffeeId, "quantity", 2))), cookie);

        assertThat(refused.getStatusCode().value()).isEqualTo(422);
        assertThat(field(refused.getBody(), "$.detail"))
                .isEqualTo("Product " + coffeeId + " appears more than once on the order.");
    }

    /**
     * A salesperson may open the order first and add lines next: an empty
     * item list is a legal draft, with a zero total the server computes.
     */
    @Test
    void aDraftMayStartEmptyWithAZeroTotal() {
        var cookie = adminCookie();
        var customerId = createCustomer(cookie, "Mercado Vazio ME",
                cnpj("181818180001"));

        var created = post("/api/orders", Map.of(
                "customerId", customerId, "items", List.of()), cookie);

        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat((List<?>) field(created.getBody(), "$.items")).isEmpty();
        assertThat(amount(created.getBody(), "$.total")).isEqualByComparingTo("0.00");
        assertThat(field(created.getBody(), "$.status")).isEqualTo("DRAFT");
    }

    /** A CNPJ whose check digits are computed by the domain's own rule. */
    private static String cnpj(String base12) {
        return com.example.bms.shared.CheckDigits.cnpjWithCheckDigits(base12);
    }

    /** The seeded admin (V2), logged in as-is. */
    private String adminCookie() {
        var response = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", "admin@bms.local", "password", "admin123")),
                String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).as("login should succeed").isTrue();
        var setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith(AuthCookie.NAME + "=");
        return setCookie.split(";", 2)[0];
    }

    private long createCustomer(String cookie, String name, String document) {
        var response = post("/api/customers", Map.of("name", name, "document", document), cookie);
        assertThat(response.getStatusCode().value()).as("customer fixture").isEqualTo(201);
        return ((Number) field(response.getBody(), "$.id")).longValue();
    }

    private long createProduct(String cookie, String sku, String name, String price) {
        var category = post("/api/categories", Map.of("name", "Mercearia " + sku), cookie);
        assertThat(category.getStatusCode().value()).as("category fixture").isEqualTo(201);
        var categoryId = ((Number) field(category.getBody(), "$.id")).longValue();
        var response = post("/api/products", Map.of(
                "sku", sku, "name", name, "price", new BigDecimal(price),
                "categoryId", categoryId), cookie);
        assertThat(response.getStatusCode().value()).as("product fixture").isEqualTo(201);
        return ((Number) field(response.getBody(), "$.id")).longValue();
    }

    private ResponseEntity<String> get(String path, String cookie) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authed(cookie)), String.class);
    }

    private ResponseEntity<String> post(String path, Object body, String cookie) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, authed(cookie)), String.class);
    }

    private ResponseEntity<String> put(String path, Object body, String cookie) {
        return rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, authed(cookie)), String.class);
    }

    private static HttpHeaders authed(String cookie) {
        var headers = new HttpHeaders();
        // Attributes (Path, Max-Age, ...) are not part of what a browser sends back.
        headers.add(HttpHeaders.COOKIE, cookie);
        return headers;
    }

    private static Object field(String body, String path) {
        return JsonPath.read(body, path);
    }

    private static BigDecimal amount(String body, String path) {
        return new BigDecimal(field(body, path).toString());
    }

    private static List<String> numbersIn(Object items) {
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) items;
        return rows.stream().map(row -> (String) row.get("number")).toList();
    }
}
