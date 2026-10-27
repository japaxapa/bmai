package com.example.bms.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seam B for the canonical form of the login email (GLOSSARY "Canonical
 * form", ticket #48): the fold AdminSeeder applies to ADMIN_EMAIL must be
 * the same function Postgres writes the row with — {@code Locale.ROOT},
 * never the JVM default — observed through the stored row against a real
 * V2 migration. The class boots with ADMIN_EMAIL as Spring parses it
 * (leading whitespace stripped, trailing preserved) — the spelling a human
 * types into the environment and reads as one email.
 */
@Testcontainers
@SpringBootTest(properties = "ADMIN_EMAIL=" + AdminSeederCanonicalFormTest.CONFIGURED_EMAIL)
class AdminSeederCanonicalFormTest {

    /** The configured spelling: padded, mixed case, and an ASCII "I" — the tr-TR hazard. */
    static final String CONFIGURED_EMAIL = "  IRMA@BMS.LOCAL  ";

    /** What every spelling of it must fold to (V2's `users_email_lowercase` shape). */
    static final String CANONICAL_EMAIL = "irma@bms.local";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * The stored-row half of the rule, over the real boot path: the
     * ADMIN_EMAIL as Spring parses it (leading whitespace stripped, trailing
     * preserved) is written in canonical form — and exactly one canonical row
     * exists. The expected form is not restated the way the seeder computes
     * it: it is Postgres's own {@code lower(btrim(...))} of the configured
     * spelling, plus the literal V2 seeded.
     */
    @Test
    void configuredEmailFoldsToOneStoredRowThatAnswersForEverySpelling() {
        var stored = jdbc.queryForObject("select email from users where role = 'ADMIN'", String.class);
        assertThat(stored)
                .as("the stored form must be the database's own lower(btrim()) of the configured spelling")
                .isEqualTo(jdbc.queryForObject("select lower(btrim(?))", String.class, CONFIGURED_EMAIL))
                .isEqualTo(CANONICAL_EMAIL);
    }

    /**
     * The trap 03bd0d1 pinned for SKU, on the seeder: the no-arg
     * {@code toLowerCase()} reads the JVM default locale, and on a tr-TR
     * deployment it stores a dotless "ı" while Postgres {@code lower()}
     * folds "I" to "i" — the row then disagrees with the database's own
     * idea of the canonical form, and a second spelling of the same email
     * can land as a second row (the exact collapse the rule exists to
     * prevent).
     *
     * <p>Under the fixed code ({@code Locale.ROOT}), the seeder computes
     * the same canonical email already stored by the boot path, so
     * {@code ApplicationRunner.run} no-ops (early return). The trap
     * triggers only when a locale bug makes the computed email differ —
     * then the runner executes an {@code UPDATE} and this assertion catches
     * the mismatch against the database's own {@code lower(btrim(...))}.
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
}
