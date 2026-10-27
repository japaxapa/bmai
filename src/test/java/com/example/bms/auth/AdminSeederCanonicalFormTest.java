package com.example.bms.auth;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seam B for the canonical form of the login email (GLOSSARY "Canonical
 * form", ticket #48): the fold AdminSeeder applies to ADMIN_EMAIL must be
 * the same function Postgres writes the row with — {@code Locale.ROOT},
 * never the JVM default — observed through the stored row and a real login
 * against a real V2 migration. The class boots with a padded, mixed-case,
 * "I"-carrying ADMIN_EMAIL: the spelling a human types into the environment
 * and reads as one email.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "ADMIN_EMAIL=" + AdminSeederCanonicalFormTest.CONFIGURED_EMAIL)
@AutoConfigureTestRestTemplate
class AdminSeederCanonicalFormTest {

    /** The configured spelling: padded, mixed case, and an ASCII "I" — the tr-TR hazard. */
    static final String CONFIGURED_EMAIL = "  IRMA@BMS.LOCAL  ";

    /** What every spelling of it must fold to (V2's `users_email_lowercase` shape). */
    static final String CANONICAL_EMAIL = "irma@bms.local";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * The stored-row half of the rule, over the real boot path: the padded,
     * mixed-case ADMIN_EMAIL from the environment is written in canonical
     * form — and then every spelling of it answers as that one account
     * (the JWT subject is the stored email, so /me shows *which* row
     * answered). The expected form is not restated the way the seeder
     * computes it: it is Postgres's own {@code lower(btrim(...))} of the
     * configured spelling, plus the literal V2 seeded.
     */
    @Test
    void configuredEmailFoldsToOneStoredRowThatAnswersForEverySpelling() {
        var stored = jdbc.queryForObject("select email from users where role = 'ADMIN'", String.class);
        assertThat(stored)
                .as("the stored form must be the database's own lower(btrim()) of the configured spelling")
                .isEqualTo(jdbc.queryForObject("select lower(btrim(?))", String.class, CONFIGURED_EMAIL))
                .isEqualTo(CANONICAL_EMAIL);

        var cookie = sessionCookieAfter(CONFIGURED_EMAIL, "admin123");
        assertThat(field(me(cookie).getBody(), "$.email"))
                .as("the padded, mixed-case spelling lands on the same stored row")
                .isEqualTo(stored);
    }

    /**
     * The trap 03bd0d1 pinned for SKU, on the seeder: the no-arg
     * {@code toLowerCase()} reads the JVM default locale, and on a tr-TR
     * deployment it stores a dotless "ı" while Postgres {@code lower()}
     * folds "I" to "i" — the row then disagrees with the database's own
     * idea of the canonical form, and a second spelling of the same email
     * can land as a second row (the exact collapse the rule exists to
     * prevent). AdminSeeder runs at boot, before any test can set the
     * locale, so the fold is exercised on the seam the component is
     * contracted on — {@code ApplicationRunner.run} — with the JVM handed
     * the locale that deployment would have. The expected answer stays the
     * database's own {@code lower(btrim(...))}.
     */
    @Test
    void seederFoldAgreesWithPostgresEvenOnATurkishJvm() {
        var deploymentLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            new AdminSeeder(jdbc, passwordEncoder, CONFIGURED_EMAIL, "").run(null);

            assertThat(jdbc.queryForObject("select email from users where role = 'ADMIN'", String.class))
                    .as("the stored form must be the database's own lower(btrim()) of the configured spelling")
                    .isEqualTo(jdbc.queryForObject("select lower(btrim(?))", String.class, CONFIGURED_EMAIL));
        } finally {
            Locale.setDefault(deploymentLocale);
            // The red path leaves the dotless spelling behind: put the boot
            // state back so the class's other test sees the row it booted with.
            jdbc.update("update users set email = ? where role = 'ADMIN'", CANONICAL_EMAIL);
        }
    }

    private String sessionCookieAfter(String email, String password) {
        var response = login(email, password);
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

    private ResponseEntity<String> login(String email, String password) {
        return rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", password)),
                String.class);
    }

    private static Object field(String body, String path) {
        return JsonPath.read(body, path);
    }
}
