package com.example.bms.supplier;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seam B (agreed with the user): the acceptance criteria end to end — real V6
 * migration, the real unique {@code document} index, the real repository and
 * the real filter chain, observed only over HTTP. This is the "demoable with
 * curl" half of ticket #7; the WebMvc slice
 * ({@link SupplierEndpointTest}) covers the same contract with the database
 * boundary stubbed.
 *
 * <p>What a stubbed slice cannot prove is here: a duplicate document is
 * refused by Postgres itself, so the 409 does not depend on a service
 * remembering to check first — and because the canonical form strips
 * punctuation, the <em>same document spelled two ways</em> must collide too.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SupplierEndToEndTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * The whole ticket in one journey: create (punctuated document on the
     * wire, canonical digits stored), see it listed in the envelope, rename
     * it, switch it off, and see the switched-off row listed again — each
     * step answering the previous one, so a broken hand-off between the write
     * and the read cannot hide behind a later step.
     */
    @Test
    void adminCanCreateListEditAndDeactivateASupplier() {
        var cookie = adminCookie();

        var created = post("/api/suppliers", Map.of(
                "name", "Torrefação Aurora LTDA",
                "document", "12.345.678/0001-95",
                "email", "contato@aurora.com.br"), cookie);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        var id = (Number) field(created.getBody(), "$.id");
        assertThat(field(created.getBody(), "$.name")).isEqualTo("Torrefação Aurora LTDA");
        assertThat(field(created.getBody(), "$.document"))
                .as("canonical form: the digits are what the API stores and echoes")
                .isEqualTo("12345678000195");
        assertThat(field(created.getBody(), "$.paymentTermsDays"))
                .as("the body omitted it, so the database's default is what was stored")
                .isEqualTo(30);
        assertThat(field(created.getBody(), "$.active"))
                .as("a supplier is born usable")
                .isEqualTo(true);

        var listed = get("/api/suppliers", cookie);
        assertThat(listed.getStatusCode().value()).isEqualTo(200);
        assertThat(namesIn(field(listed.getBody(), "$.items")))
                .as("the create above is visible in the list")
                .contains("Torrefação Aurora LTDA");

        var renamed = put("/api/suppliers/" + id, Map.of(
                "name", "Torrefação Aurora do Norte LTDA",
                "document", "12345678000195"), cookie);
        assertThat(renamed.getStatusCode().value()).isEqualTo(200);
        assertThat(field(renamed.getBody(), "$.name"))
                .isEqualTo("Torrefação Aurora do Norte LTDA");

        var deactivated = put("/api/suppliers/" + id + "/status",
                Map.of("active", false), cookie);
        assertThat(deactivated.getStatusCode().value()).isEqualTo(200);
        assertThat(field(deactivated.getBody(), "$.active")).isEqualTo(false);

        var afterDeactivation = get("/api/suppliers", cookie);
        assertThat(itemNamed(afterDeactivation.getBody(), "Torrefação Aurora do Norte LTDA"))
                .as("the rename and the deactivation both survived the round trip")
                .isNotNull();
    }

    /**
     * AC: "Constraint violations return 409/422 rendered inline in the UI" —
     * 409, not 422: the row exists and conflicts (api-design errors table),
     * rather than the payload being semantically wrong.
     *
     * <p>Every spelling runs through the same path, and this is the case only
     * seam B can raise: the second POST of the *punctuated* spelling collides
     * with the *digits* already stored, because the canonical form collapses
     * them before the unique index ever sees them. Two identical POSTs answer
     * identically whether or not anyone checked first. Each row carries its
     * own document — all three run against one database, so a shared document
     * would have the later rows colliding on their own *first* POST.
     */
    @ParameterizedTest
    @CsvSource({
        "98.765.432/0001-98, 98765432000198",     // exact repeat
        "77777777000191, '77.777.777/0001-91'",   // digits first, punctuation second
        "' 66666666000191 ', '66.666.666/0001-91'", // and padded, the same way
    })
    void duplicateDocumentIsRefusedWithProblemJsonByTheRealUniqueIndex(
            String first, String second) {
        var cookie = adminCookie();

        var created = post("/api/suppliers", Map.of(
                "name", "Aurora " + first.trim(), "document", first), cookie);
        assertThat(created.getStatusCode().value()).as("the first one is new").isEqualTo(201);

        var duplicate = post("/api/suppliers", Map.of(
                "name", "Aurora duplicada", "document", second), cookie);

        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
        assertThat(duplicate.getHeaders().getContentType().toString())
                .as("RFC 9457 body, same shape as every other refusal")
                .contains("problem+json");
        assertThat(field(duplicate.getBody(), "$.status")).isEqualTo(409);
        assertThat(field(duplicate.getBody(), "$.detail"))
                .as("the UI toasts/prints this sentence, so it cannot be blank")
                .isNotNull();
    }

    /**
     * The domain refusal against the real stack: the check-digit guard runs on
     * the entity, and the advice layer turns it into the 422 the AC names.
     * Length is refused the same way — neither is a DTO-shape problem, so
     * neither may arrive as a 400.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "529.982.247-24",  // CPF, last digit wrong
            "11.222.333/0001-82", // CNPJ, last digit wrong
            "12345",           // neither CPF nor CNPJ
    })
    void invalidDocumentIsRefusedAs422(String document) {
        var cookie = adminCookie();

        var response = post("/api/suppliers", Map.of(
                "name", "Documento inválido", "document", document), cookie);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getHeaders().getContentType().toString())
                .contains("problem+json");
        assertThat(field(response.getBody(), "$.status")).isEqualTo(422);
        assertThat(field(response.getBody(), "$.detail")).isNotNull();
    }

    /**
     * The length rule against the real stack: V6's
     * {@code suppliers_name_length} CHECK (1–120) stays the backstop for
     * write paths that bypass the entity, but a person typing into the form
     * meets the documented 400 naming the field — never the unhandled 500 a
     * CHECK violation answers when Postgres is the first to object. And no
     * row hides behind the refusal: the check runs before anything is
     * written.
     */
    @Test
    void overlongNameIsA400AndStoresNothing() {
        var cookie = adminCookie();

        var response = post("/api/suppliers", Map.of(
                "name", "x".repeat(121), "document", "11.222.333/0001-81"), cookie);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getHeaders().getContentType().toString())
                .as("RFC 9457 body, same shape as every other refusal")
                .contains("problem+json");
        assertThat(field(response.getBody(), "$.fieldErrors.name[0]"))
                .as("the field is named, so the form prints the sentence under the box")
                .isEqualTo("Name must be at most 120 characters.");
        var listed = get("/api/suppliers?search=xxxxxxxx", cookie);
        assertThat((Integer) field(listed.getBody(), "$.total"))
                .as("and nothing was stored behind the refusal")
                .isEqualTo(0);
    }

    /**
     * The range rule against the real stack, in Product's shape: the entity
     * refuses the negative terms as a 422 sentence, so V6's
     * {@code payment_terms_days >= 0} CHECK is never the first judge — against
     * a stubbed boundary the slice can only *ask* for this refusal, and only
     * here can the CHECK's 500 be shown to be gone.
     */
    @Test
    void negativePaymentTermsAreA422NotTheCheckS500() {
        var cookie = adminCookie();

        var response = post("/api/suppliers", Map.of(
                "name", "Prazo negativo LTDA", "document", "11.222.333/0001-81",
                "paymentTermsDays", -5), cookie);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getHeaders().getContentType().toString())
                .contains("problem+json");
        assertThat(field(response.getBody(), "$.detail"))
                .isEqualTo("Payment terms cannot be negative.");
    }

    /**
     * business-rules §4 row "Create/edit supplier": ADMIN and MANAGER write,
     * nobody else. The URL layer is authenticated-only, so what refuses these
     * is the {@code @PreAuthorize} on the write service.
     */
    @Test
    void employeeCannotCreateASupplier() {
        var cookie = signInAs("supplier-writer@test.local", "EMPLOYEE");

        var response = post("/api/suppliers", Map.of(
                "name", "Not mine to name", "document", "55555555000191"), cookie);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getHeaders().getContentType().toString())
                .contains("problem+json");
        assertThat(field(response.getBody(), "$.status")).isEqualTo(403);
    }

    /**
     * MANAGER is not just "not EMPLOYEE" — it genuinely carries the write, and
     * against the real filter chain (a bare {@code hasRole('ADMIN')} would
     * fail here).
     */
    @Test
    void managerMayCreateASupplier() {
        var cookie = signInAs("supplier-supervisor@test.local", "MANAGER");

        var response = post("/api/suppliers", Map.of(
                "name", "Supervisora de compras LTDA", "document", "42424242000152"), cookie);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(field(response.getBody(), "$.name"))
                .isEqualTo("Supervisora de compras LTDA");
    }

    /**
     * The other half of the agreed seam: refusing the write must not refuse
     * the read. Without this, an EMPLOYEE hitting the supplier list would be
     * locked out of data the matrix never gated.
     */
    @Test
    void signedInEmployeeCanStillListSuppliers() {
        var cookie = signInAs("supplier-reader@test.local", "EMPLOYEE");

        var response = get("/api/suppliers", cookie);

        assertThat(response.getStatusCode().value())
                .as("managing suppliers is ADMIN/MANAGER-only; reading them is not")
                .isEqualTo(200);
    }

    /**
     * The real ILIKE, the real canonical digits: a person searches by trading
     * name (any casing — ILIKE) or by the CPF/CNPJ they have in front of them.
     * The stubbed slice proves the wiring; this proves the predicate.
     */
    @Test
    void searchMatchesNameCaseInsensitivelyAndDocumentDigits() {
        var cookie = adminCookie();
        post("/api/suppliers", Map.of(
                "name", "Fornecimento Bandeirantes LTDA",
                "document", "11111111000191"), cookie);

        var byName = get("/api/suppliers?search=BANDEIRANTES", cookie);
        assertThat(byName.getStatusCode().value()).isEqualTo(200);
        assertThat(namesIn(field(byName.getBody(), "$.items")))
                .as("ILIKE: the term's casing must not matter")
                .contains("Fornecimento Bandeirantes LTDA");

        var byDocument = get("/api/suppliers?search=11111111000191", cookie);
        assertThat(byDocument.getStatusCode().value()).isEqualTo(200);
        assertThat((Integer) field(byDocument.getBody(), "$.total")).isEqualTo(1);
    }

    /**
     * api-design's search promise, and the cell no other test can carry: the
     * stored document is canonical digits, but the person types the punctuated
     * CNPJ in front of them — the term is normalized to digits for the
     * document column *only* (the name half must match what was typed). The
     * slice's stub carries no normalization, so this is seam B's by cell
     * delegation; without this case {@code digitsOf} could vanish and the
     * suite would stay green while the documented promise broke silently.
     */
    @Test
    void searchingByThePunctuatedSpellingFindsTheRowStoredAsDigits() {
        var cookie = adminCookie();
        // A CNPJ no other fixture holds, check digits computed by the very
        // rule the entity judges with — one copy of the arithmetic (§5).
        var digits = CheckDigits.cnpjWithCheckDigits("554433221100");
        var punctuated = digits.substring(0, 2) + "." + digits.substring(2, 5) + "."
                + digits.substring(5, 8) + "/" + digits.substring(8, 12)
                + "-" + digits.substring(12);
        var created = post("/api/suppliers", Map.of(
                "name", "Busca Pontuada LTDA", "document", punctuated), cookie);
        assertThat(created.getStatusCode().value()).isEqualTo(201);

        var found = get("/api/suppliers?search=" + punctuated, cookie);

        assertThat(found.getStatusCode().value()).isEqualTo(200);
        assertThat(namesIn(field(found.getBody(), "$.items")))
                .as("the punctuated spelling finds the row stored as digits (api-design)")
                .containsExactly("Busca Pontuada LTDA");
    }

    /**
     * The seeded admin (V2), logged in as-is: nothing is inserted here, so the
     * journey runs against exactly the account the README documents.
     */
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

    private static List<String> namesIn(Object items) {
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) items;
        return rows.stream().map(row -> (String) row.get("name")).toList();
    }

    private static Map<String, Object> itemNamed(String body, String name) {
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) field(body, "$.items");
        return rows.stream().filter(row -> name.equals(row.get("name"))).findFirst().orElse(null);
    }
}
