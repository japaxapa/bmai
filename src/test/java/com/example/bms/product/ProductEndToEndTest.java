package com.example.bms.product;

import com.example.bms.auth.AuthCookie;
import com.example.bms.shared.CheckDigits;
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
import java.util.Locale;
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
        assertThat(field(created.getBody(), "$.sku"))
                .as("the API answers the canonical form, not the spelling posted (GLOSSARY)")
                .isEqualTo(sku.toLowerCase());
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
                .as("the create above is visible in the list, under its canonical SKU")
                .contains(sku.toLowerCase());

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
        assertThat(itemSkued(after.getBody(), sku.toLowerCase()))
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
     * <p>Every spelling runs through that same path: "cafe-1" after
     * "CAFE-1" repeats the SKU, and so does " CAFE-1 " — the canonical form
     * (GLOSSARY) collapses them before the index ever sees them, so none of
     * them can invent a second product.
     */
    @ParameterizedTest
    @CsvSource({
        "EXACT",
        "OTHER-CASE",
        "PADDED",
    })
    void duplicateSkuIsRefusedWithProblemJsonByTheRealUniqueIndex(String repeat) {
        var cookie = adminCookie();
        var categoryId = createCategoryNamed(uniqueName("Bebidas"), cookie);
        // One SKU per invocation, spelled twice: the exact repeat, another
        // case, or the same SKU padded with spaces — all must collide with
        // *this* test's own row, not trip over another test's data.
        var sku = uniqueSku("CAFE-1");
        var secondSpelling = switch (repeat) {
            case "EXACT" -> sku;
            case "PADDED" -> "  " + sku + "  ";
            default -> sku.toLowerCase();
        };

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
     * Canonical form must be the same function on both sides of the fence:
     * the entity lowercases with {@code Locale.ROOT}, Postgres lower()s with
     * the database collation, and V5's CHECK plus the {@code lower(sku)}
     * index compare the two. This SKU carries the hazards — surrounding
     * padding, an accented letter, and the ASCII "I" a tr-TR JVM would fold
     * into a dotless ı (the classic no-arg {@code toLowerCase()} trap). The
     * stored form must equal the database's own lower() of the posted
     * spelling, and a differently-cased repeat must still collide on the
     * index: entity and database agreeing *is* the "one SKU" promise.
     */
    @Test
    void canonicalSkuAgreesWithTheDatabasesOwnLower() {
        var cookie = adminCookie();
        var categoryId = createCategoryNamed(uniqueName("Padaria"), cookie);
        var posted = "  Café-I-" + sequence.incrementAndGet() + "  ";

        var created = post("/api/products", newProduct(posted, categoryId), cookie);
        assertThat(created.getStatusCode().value()).isEqualTo(201);

        var stored = (String) field(created.getBody(), "$.sku");
        var databaseSays = jdbc.queryForObject("select lower(btrim(?))", String.class, posted);
        assertThat(stored)
                .as("entity canonical form == the database's own lower() of the posted SKU")
                .isEqualTo(databaseSays);

        var duplicate = post("/api/products",
                newProduct(posted.toUpperCase(Locale.ROOT), categoryId), cookie);
        assertThat(duplicate.getStatusCode().value())
                .as("the index's lower() and the entity's canonical form see one SKU")
                .isEqualTo(409);
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
                .as("the stored SKU is canonical (lowercase), and the match reaches it")
                .containsExactly(cafeSku.toLowerCase());

        var byName = get("/api/products?search=500G", cookie);
        assertThat(skusIn(field(byName.getBody(), "$.items")))
                .as("the name column searches case-insensitively too — names keep their casing, so this is ILIKE's own work")
                .contains(cafeSku.toLowerCase())
                .doesNotContain(trigoSku.toLowerCase());

        var noMatch = get("/api/products?search=nao-existe", cookie);
        assertThat((Integer) field(noMatch.getBody(), "$.total")).isZero();
    }

    /**
     * The envelope must tell the truth about one set: {@code items} come
     * from the repository's findAll, {@code total} from its count — both
     * filtered by the one shared predicate (JdbcProductRepository
     * SEARCH_PREDICATE). A search matching several rows therefore pins what
     * a drifted copy would break: total equals the matched rows, and paging
     * through them never gains or loses one.
     */
    @Test
    void searchTotalMatchesTheListedRows() {
        var cookie = adminCookie();
        var categoryId = createCategoryNamed(uniqueName("Padaria"), cookie);
        // One unique term, several rows — carried by the SKU alone (names
        // stay unrelated), so a count that filters a different column set
        // than findAll sees zero: that is the drift this test pins.
        var term = "DRIFT-" + sequence.incrementAndGet();
        for (int i = 0; i < 3; i++) {
            assertThat(post("/api/products", Map.of(
                    "sku", uniqueSku(term + "-" + i), "name", "Produto sem o termo " + i,
                    "price", "10.00", "categoryId", categoryId), cookie)
                    .getStatusCode().value()).isEqualTo(201);
        }

        var found = get("/api/products?search=" + term, cookie);

        assertThat((Integer) field(found.getBody(), "$.total"))
                .as("count saw the same rows findAll listed")
                .isEqualTo(3);
        assertThat(skusIn(field(found.getBody(), "$.items")))
                .as("and every one of them is on the page")
                .hasSize(3);
    }

    /**
     * Ticket 7's product link, through the real stack: V6's nullable column,
     * the FK, and the LEFT join that resolves the supplier's *name* for the
     * row. Two things a stubbed slice cannot prove: a product with no
     * supplier still appears in the list (an inner join would hide it), and
     * the null link arrives as null rather than as a sentinel number the UI
     * would have to know to filter out.
     */
    @Test
    void aProductListsWithItsSupplierNameAndWithoutOneItStillAppears() {
        var cookie = adminCookie();
        var categoryId = createCategoryNamed(uniqueName("Fornecedores"), cookie);
        var supplierName = "Aurora " + sequence.incrementAndGet();
        var supplierId = createSupplierNamed(supplierName, cookie);
        var withSupplier = uniqueSku("COM-FORNECEDOR");
        var withoutSupplier = uniqueSku("SEM-FORNECEDOR");

        var created = post("/api/products", Map.of(
                "sku", withSupplier, "name", "Café com fornecedor",
                "price", "24.90", "categoryId", categoryId,
                "supplierId", supplierId), cookie);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(((Number) field(created.getBody(), "$.supplierId")).longValue())
                .as("the link round-trips as the id that was posted")
                .isEqualTo(supplierId);
        assertThat(field(created.getBody(), "$.supplierName"))
                .as("and the UI renders the name, so the API resolves it")
                .isEqualTo(supplierName);

        var listed = get("/api/products", cookie);
        assertThat(itemSkued(listed.getBody(), withSupplier.toLowerCase()))
                .as("the LEFT join carries the supplier's name into the list")
                .isNotNull();

        var unlinked = post("/api/products", Map.of(
                "sku", withoutSupplier, "name", "Café sem fornecedor",
                "price", "19.90", "categoryId", categoryId), cookie);
        assertThat(unlinked.getStatusCode().value())
                .as("the link is optional — a product may be listed before anyone "
                        + "decides who supplies it")
                .isEqualTo(201);
        assertThat(field(unlinked.getBody(), "$.supplierId"))
                .as("null, not a sentinel the UI would have to filter out")
                .isNull();
        assertThat(field(unlinked.getBody(), "$.supplierName"))
                .as("and the name that would have resolved from it")
                .isNull();

        assertThat(itemSkued(get("/api/products", cookie).getBody(), withoutSupplier.toLowerCase()))
                .as("a product with no supplier is still a product: the join must not "
                        + "make it invisible in the list")
                .isNotNull();
    }

    /**
     * The supplier link is a real reference, so an unknown id answers the
     * same 404 the category does — not a 500 whose body leaks the SQL to
     * whoever posted it.
     */
    @Test
    void anUnknownSupplierOnCreateIsA404AndStoresNothing() {
        var cookie = adminCookie();
        var categoryId = createCategoryNamed(uniqueName("Fornecedor-404"), cookie);
        var sku = uniqueSku("FORNECEDOR-404");

        var refused = post("/api/products", Map.of(
                "sku", sku, "name", "Fornecedor inventado",
                "price", "10.00", "categoryId", categoryId,
                "supplierId", 999_999L), cookie);

        assertThat(refused.getStatusCode().value()).isEqualTo(404);
        assertThat(refused.getHeaders().getContentType().toString())
                .as("RFC 9457 body, same shape as every other refusal")
                .contains("problem+json");
        assertThat(field(refused.getBody(), "$.status")).isEqualTo(404);
        assertThat(skusIn(field(get("/api/products?search=" + sku, cookie).getBody(), "$.items")))
                .as("and nothing was stored behind the refusal")
                .isEmpty();
    }

    /**
     * The edit half of the same rule, and the half only this seam can pin:
     * {@code edit} must resolve the supplier *before* {@code store(...)}.
     * Resolving after it trips V6's foreign key inside the UPDATE and answers
     * an untranslated 500 instead of the documented 404 — the row survives
     * only because the transaction rolled back, which no assertion at the
     * HTTP seam would otherwise notice.
     */
    @Test
    void anUnknownSupplierOnEditIsA404AndChangesNothing() {
        var cookie = adminCookie();
        var categoryId = createCategoryNamed(uniqueName("Fornecedor-404-edit"), cookie);
        var sku = uniqueSku("FORNECEDOR-404-EDIT");
        var created = post("/api/products", Map.of(
                "sku", sku, "name", "Café fiel", "price", "10.00",
                "categoryId", categoryId), cookie);
        var id = (Number) field(created.getBody(), "$.id");

        var refused = put("/api/products/" + id, Map.of(
                "sku", sku, "name", "Café trocado", "price", "99.00",
                "categoryId", categoryId, "supplierId", 999_999L), cookie);

        assertThat(refused.getStatusCode().value())
                .as("the unknown id is a 404 resolved before the write, exactly as on create")
                .isEqualTo(404);
        assertThat(refused.getHeaders().getContentType().toString())
                .as("RFC 9457 body, same shape as every other refusal")
                .contains("problem+json");
        assertThat(field(refused.getBody(), "$.status")).isEqualTo(404);
        var row = itemSkued(get("/api/products?search=" + sku, cookie).getBody(), sku.toLowerCase());
        assertThat(row).isNotNull();
        assertThat(row.get("name"))
                .as("and the refusal came before the UPDATE — the stored row is untouched")
                .isEqualTo("Café fiel");
        assertThat(row.get("price")).isEqualTo(10.00);
    }

    /** Creates a supplier through its own API and answers its id. */
    private long createSupplierNamed(String name, String cookie) {
        var response = post("/api/suppliers", Map.of(
                "name", name, "document", uniqueDocument()), cookie);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return ((Number) field(response.getBody(), "$.id")).longValue();
    }

    /**
     * A 14-digit CNPJ whose check digits the domain will accept: an 12-digit
     * body padded from the counter (unique per invocation), then the two
     * modulus-11 digits that body implies — computed by
     * {@link CheckDigits#cnpjWithCheckDigits}, the very rule the domain
     * judges with, so there is one copy of the arithmetic in the repo rather
     * than a production copy and a test copy that must be kept in step.
     * Computed rather than hardcoded — a fixture table would go stale the
     * moment a test wanted a second supplier.
     */
    private String uniqueDocument() {
        var body = "%d".formatted(sequence.incrementAndGet() % 10_000_000_000L);
        if (body.length() < 12) {
            body = "0".repeat(12 - body.length()) + body;
        }
        return CheckDigits.cnpjWithCheckDigits(body);
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
