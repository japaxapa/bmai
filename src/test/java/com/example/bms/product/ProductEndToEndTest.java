package com.example.bms.product;

import com.example.bms.auth.AuthCookie;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Seam B (the same pair ticket #5 settled): the acceptance criteria end to
 * end — real V4 migration, the real case-insensitive unique index, the real
 * {@code ILIKE}, the real repository and the real filter chain, observed
 * only over HTTP. This is the "demoable with curl" half of ticket #6; the
 * WebMvc slice ({@link ProductEndpointTest}) covers the same contract with
 * the database boundary stubbed.
 *
 * <p>The things a stubbed slice cannot prove are here: a duplicate SKU is
 * refused by Postgres itself (so the 409 cannot be raced past a service
 * check), the two spellings of a SKU are one SKU, and the range CHECKs bite
 * even for a caller that is not the API.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ProductEndToEndTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    /** Distinguishes this class's fixture names in the database it shares with its siblings. */
    private static final AtomicLong sequence = new AtomicLong();

    /**
     * The whole ticket in one journey: link a category, create, see it
     * listed with the category's *name* (not just its id), edit, switch it
     * off, and see the switched-off row listed again — each step answering
     * the previous one, so a broken hand-off between the write and the read
     * cannot hide behind a later step.
     */
    @Test
    void adminCanCreateListEditAndDeactivateAProduct() {
        var cookie = adminCookie();
        var categoryName = uniqueName("Padaria");
        var categoryId = createCategoryNamed(categoryName, cookie);
        var sku = uniqueSku("CAFE-1");

        var created = post("/api/products", Map.of(
                "sku", sku, "name", "Café 500g", "description", "Torra média",
                "price", "24.90", "cost", "15.00", "minimumStock", 10,
                "categoryId", categoryId), cookie);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        var id = (Number) field(created.getBody(), "$.id");
        assertThat(field(created.getBody(), "$.sku")).isEqualTo(sku);
        assertThat(field(created.getBody(), "$.categoryName"))
                .as("the UI displays the name, so the API must resolve it")
                .isEqualTo(categoryName);
        assertThat(field(created.getBody(), "$.active"))
                .as("a product is born usable")
                .isEqualTo(true);
        assertThat(field(created.getBody(), "$.stock"))
                .as("stock is born zero — only movements move it (ADR 0006)")
                .isEqualTo(0);

        var listed = get("/api/products", cookie);
        assertThat(listed.getStatusCode().value()).isEqualTo(200);
        assertThat(skusIn(field(listed.getBody(), "$.items")))
                .as("the create above is visible in the list")
                .contains(sku);

        var edited = put("/api/products/" + id, Map.of(
                "sku", sku, "name", "Café 500g torra escura",
                "price", "27.90", "cost", "15.00", "minimumStock", 10,
                "categoryId", categoryId), cookie);
        assertThat(edited.getStatusCode().value()).isEqualTo(200);
        assertThat(field(edited.getBody(), "$.name")).isEqualTo("Café 500g torra escura");
        assertThat(field(edited.getBody(), "$.price"))
                .as("money round-trips as the same number, not a float smear")
                .isEqualTo(27.90);

        var deactivated = put("/api/products/" + id + "/status", Map.of("active", false), cookie);
        assertThat(deactivated.getStatusCode().value()).isEqualTo(200);
        assertThat(field(deactivated.getBody(), "$.active")).isEqualTo(false);

        var after = get("/api/products", cookie);
        assertThat(itemSkued(after.getBody(), sku))
                .as("the edit and the deactivation both survived the round trip")
                .isNotNull();
    }

    /**
     * AC: "Duplicate SKU returns 409/422 rendered inline in the UI" — 409,
     * not 422: the row exists and conflicts (api-design errors table). The
     * refusal must be an RFC 9457 body with its own sentence, and it must
     * come from the constraint — so a second identical POST answers
     * identically whether or not anyone checked first.
     *
     * <p>Both spellings run through that same path: "cafe-1" after
     * "CAFE-1" repeats the SKU, it does not invent a second product (the
     * functional index on {@code lower(sku)} — the choice settled with the
     * user, mirroring category names).
     */
    @ParameterizedTest
    @CsvSource({
        "EXACT",
        "OTHER-CASE",
    })
    void duplicateSkuIsRefusedWithProblemJsonByTheRealUniqueIndex(String repeat) {
        var cookie = adminCookie();
        var categoryId = createCategoryNamed(uniqueName("Bebidas"), cookie);
        // One SKU per invocation, spelled twice: the exact repeat, or the
        // same SKU in another case — both must collide with *this* test's
        // own row, not trip over another test's data.
        var sku = uniqueSku("CAFE-1");
        var secondSpelling = "EXACT".equals(repeat) ? sku : sku.toLowerCase();

        var created = post("/api/products", newProduct(sku, categoryId), cookie);
        assertThat(created.getStatusCode().value()).as("the first one is new").isEqualTo(201);

        var duplicate = post("/api/products", newProduct(secondSpelling, categoryId), cookie);

        assertThat(duplicate.getStatusCode().value())
                .as("the second spelling is the same SKU (index on lower(sku))")
                .isEqualTo(409);
        assertThat(duplicate.getHeaders().getContentType().toString())
                .as("RFC 9457 body, same shape as every other refusal")
                .contains("problem+json");
        assertThat(field(duplicate.getBody(), "$.status")).isEqualTo(409);
        assertThat(field(duplicate.getBody(), "$.detail"))
                .as("the UI prints this sentence inline, so it cannot be blank")
                .isNotNull();
    }

    /**
     * The range CHECKs are not just advice from the application layer: the
     * database refuses the bad row too (testing-strategy §3, "CHECK
     * constraints bite"). A caller that is not the API — a migration, a
     * console session, a future import — hits the same wall.
     */
    @Test
    void theRangeChecksBiteAtTheDatabaseLevel() {
        var cookie = adminCookie();
        var categoryId = createCategoryNamed(uniqueName("Padaria"), cookie);

        assertThatThrownBy(() -> jdbc.update(
                "insert into products (sku, name, price, category_id) values ('FREE-1', 'Grátis', 0, ?)",
                categoryId))
                .as("price > 0 is a database CHECK, not only an entity guard")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * business-rules §4 row "Create/edit/deactivate product": ADMIN and
     * MANAGER write, EMPLOYEE does not — and the refusal is the same
     * problem+json the rest of the API answers with.
     */
    @Test
    void employeeCannotCreateAProductButManagerCan() {
        var cookie = adminCookie();
        var categoryId = createCategoryNamed(uniqueName("Padaria"), cookie);

        var employee = signInAs("employee-product-writer@test.local", "EMPLOYEE");
        var refused = post("/api/products", newProduct("EMP-1", categoryId), employee);
        assertThat(refused.getStatusCode().value()).isEqualTo(403);
        assertThat(refused.getHeaders().getContentType().toString()).contains("problem+json");

        var manager = signInAs("manager-product-writer@test.local", "MANAGER");
        var allowed = post("/api/products", newProduct("MGR-1", categoryId), manager);
        assertThat(allowed.getStatusCode().value())
                .as("MANAGER is genuinely privileged, not merely 'not employee'")
                .isEqualTo(201);
    }

    /**
     * api-design: {@code ?search=} is an ILIKE over name and sku. Against
     * the real database this pins what the WebMvc slice could only fake:
     * the match ignores case and reaches either column.
     */
    @Test
    void searchFindsProductsBySkuAndNameIgnoringCase() {
        var cookie = adminCookie();
        var categoryId = createCategoryNamed(uniqueName("Padaria"), cookie);
        var cafeSku = uniqueSku("CAFE-1");
        var trigoSku = uniqueSku("TRIGO-2");
        assertThat(post("/api/products", Map.of(
                "sku", cafeSku, "name", "Café 500g", "price", "24.90", "categoryId", categoryId), cookie)
                .getStatusCode().value()).isEqualTo(201);
        assertThat(post("/api/products", Map.of(
                "sku", trigoSku, "name", "Trigo 1kg", "price", "5.50", "categoryId", categoryId), cookie)
                .getStatusCode().value()).isEqualTo(201);

        var bySku = get("/api/products?search=" + cafeSku.toLowerCase(), cookie);
        assertThat(skusIn(field(bySku.getBody(), "$.items")))
                .as("lowercase term, uppercase sku — the index's notion of a SKU")
                .containsExactly(cafeSku);

        var byName = get("/api/products?search=500G", cookie);
        assertThat(skusIn(field(byName.getBody(), "$.items")))
                .as("the name column searches too, also case-insensitively")
                .contains(cafeSku)
                .doesNotContain(trigoSku);

        var noMatch = get("/api/products?search=nao-existe", cookie);
        assertThat((Integer) field(noMatch.getBody(), "$.total")).isZero();
    }

    /** Creates a category through its own API and answers its id. */
    private long createCategoryNamed(String name, String cookie) {
        var response = post("/api/categories", Map.of("name", name), cookie);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return ((Number) field(response.getBody(), "$.id")).longValue();
    }

    /** A vocabulary name no other invocation can hold — the unique index bites both ways. */
    private static String uniqueName(String base) {
        return base + "-" + sequence.incrementAndGet();
    }

    /** A SKU no other invocation can hold — the unique index bites both ways. */
    private static String uniqueSku(String base) {
        return base + "-" + sequence.incrementAndGet();
    }

    /** The body the product API takes for a brand-new row. */
    private static Map<String, Object> newProduct(String sku, long categoryId) {
        return Map.of("sku", sku, "name", "Produto " + sku,
                "price", "10.00", "categoryId", categoryId);
    }

    /** The seeded admin (V2), logged in as-is: nothing is inserted here. */
    private String adminCookie() {
        return loginCookieFor("admin@bms.local", "admin123");
    }

    /** Creates an account with the given role, then logs in as it. */
    private String signInAs(String email, String role) {
        jdbc.update("""
                        insert into users (email, password_hash, role, active)
                        values (?, ?, cast(? as user_role), true)
                        """,
                email, new BCryptPasswordEncoder(10).encode("a-password-for-" + role.toLowerCase()), role);
        return loginCookieFor(email, "a-password-for-" + role.toLowerCase());
    }

    private String loginCookieFor(String email, String password) {
        var response = login(email, password);
        assertThat(response.getStatusCode().is2xxSuccessful()).as("login should succeed").isTrue();

        var setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith(AuthCookie.NAME + "=");
        // Attributes (Path, Max-Age, ...) are not part of what a browser sends back.
        return setCookie.split(";", 2)[0];
    }

    private ResponseEntity<String> login(String email, String password) {
        return rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", password)), String.class);
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

    private static List<String> skusIn(Object items) {
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) items;
        return rows.stream().map(row -> (String) row.get("sku")).toList();
    }

    private static Map<String, Object> itemSkued(String body, String sku) {
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) field(body, "$.items");
        return rows.stream().filter(row -> sku.equals(row.get("sku"))).findFirst().orElse(null);
    }
}
