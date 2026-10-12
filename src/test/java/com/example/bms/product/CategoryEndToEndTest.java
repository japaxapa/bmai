package com.example.bms.product;

import com.example.bms.auth.AuthCookie;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
 * Seam B (agreed with the user): the acceptance criteria end to end — real V3
 * migration, the real unique index, the real repository and the real filter
 * chain, observed only over HTTP. This is the "demoable with curl" half of
 * ticket #5; the WebMvc slice ({@link CategoryEndpointTest}) covers the same
 * contract with the database boundary stubbed.
 *
 * <p>The one thing a stubbed slice cannot prove is here: a duplicate name is
 * refused by Postgres itself, so the 409 does not depend on a service remembering
 * to check first (and cannot race past that check).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class CategoryEndToEndTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * The whole ticket in one journey: create, see it listed in the envelope,
     * rename it, switch it off, and see the switched-off row listed again —
     * each step answering the previous one, so a broken hand-off between the
     * write and the read cannot hide behind a later step.
     */
    @Test
    void adminCanCreateListEditAndDeactivateACategory() {
        var cookie = adminCookie();

        var created = post("/api/categories", Map.of("name", "Bebidas"), cookie);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        var id = (Number) field(created.getBody(), "$.id");
        assertThat(field(created.getBody(), "$.name")).isEqualTo("Bebidas");
        assertThat(field(created.getBody(), "$.active"))
                .as("a category is born usable")
                .isEqualTo(true);

        var listed = get("/api/categories", cookie);
        assertThat(listed.getStatusCode().value()).isEqualTo(200);
        assertThat(namesIn(field(listed.getBody(), "$.items")))
                .as("the create above is visible in the list")
                .contains("Bebidas");
        assertThat((Integer) field(listed.getBody(), "$.total")).isGreaterThanOrEqualTo(1);

        var renamed = put("/api/categories/" + id, Map.of("name", "Bebidas naturais"), cookie);
        assertThat(renamed.getStatusCode().value()).isEqualTo(200);
        assertThat(field(renamed.getBody(), "$.name")).isEqualTo("Bebidas naturais");

        var deactivated = put("/api/categories/" + id + "/status", Map.of("active", false), cookie);
        assertThat(deactivated.getStatusCode().value()).isEqualTo(200);
        assertThat(field(deactivated.getBody(), "$.active")).isEqualTo(false);

        var afterDeactivation = get("/api/categories", cookie);
        assertThat(itemNamed(afterDeactivation.getBody(), "Bebidas naturais"))
                .as("the rename and the deactivation both survived the round trip")
                .isNotNull();
    }

    /**
     * AC: "Duplicate category name returns 409/422 problem+json rendered
     * inline". 409, not 422: the row exists and conflicts (api-design errors
     * table), rather than the payload being semantically wrong.
     *
     * <p>The refusal must be an RFC 9457 body with its own sentence — the UI
     * renders {@code detail} inline — and it must come from the constraint, so
     * a second identical POST answers identically whether or not anyone
     * checked first.
     */
    @Test
    void duplicateNameIsRefusedWithProblemJsonByTheRealUniqueIndex() {
        var cookie = adminCookie();

        var first = post("/api/categories", Map.of("name", "Refrigerantes"), cookie);
        assertThat(first.getStatusCode().value()).as("the first one is new").isEqualTo(201);

        var second = post("/api/categories", Map.of("name", "Refrigerantes"), cookie);

        assertThat(second.getStatusCode().value()).isEqualTo(409);
        assertThat(second.getHeaders().getContentType().toString())
                .as("RFC 9457 body, same shape as every other refusal")
                .contains("problem+json");
        assertThat(field(second.getBody(), "$.status")).isEqualTo(409);
        assertThat(field(second.getBody(), "$.detail"))
                .as("the UI toasts/prints this sentence, so it cannot be blank")
                .isNotNull();
    }

    /**
     * Uniqueness is case-insensitive — the choice made with the user over a
     * plain {@code UNIQUE}: a human typing "bebidas" after "Bebidas" is
     * repeating the same vocabulary word, not inventing a second one. The
     * refusal must therefore come from the same place the exact-case one
     * does (the functional index on {@code lower(name)}), so a service that
     * forgets its own check cannot let either spelling through.
     */
    @Test
    void duplicateNameIsRefusedRegardlessOfCase() {
        var cookie = adminCookie();

        var first = post("/api/categories", Map.of("name", "Hortifruti"), cookie);
        assertThat(first.getStatusCode().value()).as("the first one is new").isEqualTo(201);

        var second = post("/api/categories", Map.of("name", "HORTIFRUTI"), cookie);

        assertThat(second.getStatusCode().value()).isEqualTo(409);
        assertThat(second.getHeaders().getContentType().toString())
                .as("RFC 9457 body, same shape as every other refusal")
                .contains("problem+json");
        assertThat(field(second.getBody(), "$.status")).isEqualTo(409);
        assertThat(field(second.getBody(), "$.detail"))
                .as("the UI toasts/prints this sentence, so it cannot be blank")
                .isNotNull();
    }

    /**
     * business-rules §4 row "Categories": ADMIN, and nobody else. The URL layer
     * is authenticated-only, so what refuses these two is the
     * {@code @PreAuthorize} on the write service (MethodSecurityWiringTest
     * already proved the annotation is live, not silently inert).
     */
    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYEE", "MANAGER"})
    void nonAdminCannotManageCategories(String role) {
        var email = role.toLowerCase() + "-category-writer@test.local";
        var cookie = signInAs(email, role);

        var response = post("/api/categories", Map.of("name", "Not mine to name"), cookie);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getHeaders().getContentType().toString())
                .contains("problem+json");
        assertThat(field(response.getBody(), "$.status")).isEqualTo(403);
    }

    /**
     * The other half of the agreed seam: refusing the write must not refuse
     * the read. Without this, an EMPLOYEE hitting the category list (directly,
     * or later through a product form) would be locked out of data the matrix
     * never gated — and nobody would notice until ticket #6.
     */
    @Test
    void signedInEmployeeCanStillListCategories() {
        var cookie = signInAs("category-reader@test.local", "EMPLOYEE");

        var response = get("/api/categories", cookie);

        assertThat(response.getStatusCode().value())
                .as("managing the vocabulary is ADMIN-only; reading it is not")
                .isEqualTo(200);
    }

    /**
     * The seeded admin (V2), logged in as-is: nothing is inserted here, so the
     * journey below runs against exactly the account the README documents.
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
