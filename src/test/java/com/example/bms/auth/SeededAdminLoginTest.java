package com.example.bms.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seam B (agreed with the user): the acceptance criteria end to end — real V2
 * migration, the real bcrypt hash it seeds, the real repository and the real
 * filter chain, observed only over HTTP. This is the "demoable with curl"
 * ticket 3 asks for; the WebMvc slice (AuthEndpointTest) covers the same
 * contract with the database boundary stubbed.
 *
 * With no ADMIN_EMAIL / ADMIN_PASSWORD configured, the documented defaults are
 * the ones that answer — which is also what pins AdminSeeder's no-op path.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SeededAdminLoginTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void seededAdminLogsInAndProvesWhoTheyAre() {
        var cookie = sessionCookieAfter("admin@bms.local", "admin123");

        var me = me(cookie);
        assertThat(me.getStatusCode().is2xxSuccessful()).as("/me answers the cookie").isTrue();
        assertThat(field(me.getBody(), "$.email")).isEqualTo("admin@bms.local");
        assertThat(field(me.getBody(), "$.role")).isEqualTo("ADMIN");
    }

    @Test
    void loginDoesNotDependOnHowTheEmailIsTyped() {
        var cookie = sessionCookieAfter("ADMIN@BMS.LOCAL", "admin123");

        assertThat(field(me(cookie).getBody(), "$.role"))
                .as("the stored email is lowercase, so the lookup normalises too")
                .isEqualTo("ADMIN");
    }

    /**
     * The ways a login can be wrong, against the real table: unknown email,
     * wrong password, and a missing field on either side — a valid account
     * with no password must not become a 500 either. All must answer
     * identically or user enumeration is possible.
     */
    static Stream<Map<String, String>> badCredentials() {
        return Stream.of(
                Map.of("password", "wrong-password"),
                Map.of("email", "nobody@bms.local", "password", "wrong-password"),
                Map.of("email", "admin@bms.local", "password", "wrong-password"),
                Map.of("email", "admin@bms.local"));
    }

    @ParameterizedTest
    @MethodSource("badCredentials")
    void badCredentialsAreRejectedWithTheSameProblemBody(Map<String, String> credentials) {
        var response = login(credentials);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getContentType().toString())
                .as("RFC 9457 body, same shape as every other refusal")
                .contains("problem+json");
        assertThat(field(response.getBody(), "$.status")).isEqualTo(401);
        assertThat(field(response.getBody(), "$.detail")).isEqualTo("Invalid email or password");
    }

    /**
     * Deactivation is how this system revokes access (requirements §2:
     * "Deactivate, never delete"), so a deactivated account's *correct*
     * password must stop answering — and answer exactly like any other bad
     * login, or the refusal would announce that the account exists and was
     * switched off.
     */
    @Test
    void deactivatedUserCannotLogInEvenWithTheRightPassword() {
        var password = "still-the-right-password";
        jdbc.update("insert into users (email, password_hash, role, active) values (?, ?, cast(? as user_role), false)",
                "fired@bms.local", new BCryptPasswordEncoder(10).encode(password), "EMPLOYEE");

        var response = login(Map.of("email", "fired@bms.local", "password", password));

        assertThat(response.getStatusCode().value())
                .as("credentials are valid but the account is deactivated")
                .isEqualTo(401);
        assertThat(field(response.getBody(), "$.detail")).isEqualTo("Invalid email or password");
    }

    private String sessionCookieAfter(String email, String password) {
        var response = login(Map.of("email", email, "password", password));
        assertThat(response.getStatusCode().is2xxSuccessful()).as("login should succeed").isTrue();

        var setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith(AuthCookie.NAME + "=");
        // Attributes (Path, Max-Age, ...) are not part of what a browser sends back.
        return setCookie.split(";", 2)[0];
    }

    private ResponseEntity<String> me(String cookie) {
        var headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return rest.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> login(Map<String, String> credentials) {
        return rest.postForEntity("/api/auth/login", new HttpEntity<>(credentials), String.class);
    }

    private static Object field(String body, String path) {
        return JsonPath.read(body, path);
    }
}
