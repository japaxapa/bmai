package com.example.bms;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Seam 2 (agreed with the user): Flyway's migration history.
 *
 * Observed through Flyway's own public API: the schema state is exactly what
 * Flyway records, not what we guess from internal state.
 */
@Testcontainers
@SpringBootTest
class FlywayMigrationsTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Ticket 3, acceptance criterion 1: the seed migration creates an ADMIN
     * user with known credentials, so the app is demoable with zero config.
     *
     * The hash is verified with BCrypt itself rather than compared to a stored
     * literal: the database carries a hash we cannot recompute here, so the
     * independent source of truth is the algorithm checking the known password.
     */
    @Test
    void seedMigrationCreatesAnAdminWithKnownCredentials() {
        var admin = jdbc.queryForMap(
                "select password_hash, role, active from users where email = ?",
                "admin@bms.local");

        assertThat(admin.get("role")).asString()
                .as("the seeded user owns the system")
                .isEqualTo("ADMIN");
        assertThat(admin.get("active"))
                .as("a deactivated admin cannot log in")
                .isEqualTo(true);
        assertThat(new BCryptPasswordEncoder().matches("admin123", admin.get("password_hash").toString()))
                .as("the documented default password verifies against the stored hash")
                .isTrue();
    }

    @Test
    void initialMigrationIsAppliedOnFirstRun() {
        assertThat(pendingVersions()).as("no pending migrations after boot").isEmpty();
        assertThat(appliedVersions())
                .as("the initial migration (V1) should have been applied")
                .contains("1");
    }

    @Test
    void secondMigrateRunIsANoOp() {
        List<String> before = appliedVersions();

        flyway.migrate(); // what a restart does: Boot runs this again

        assertThat(appliedVersions())
                .as("re-running migrations must not apply anything new")
                .isEqualTo(before);
        assertThat(pendingVersions()).isEmpty();
    }

    /**
     * Ticket #41 (folded into V4): business-rules §5 declares the category
     * name as 1–120 chars, and since V4 the *database* says so too — not
     * only the request DTO's {@code @Size}. Both edges of the range bite:
     * the overlong name, and the empty one the CHECK can see even when a
     * future caller bypasses the API's {@code @NotBlank}.
     */
    @Test
    void theCategoryNameLengthCheckBitesAtTheDatabaseLevel() {
        assertThatThrownBy(() ->
                jdbc.update("insert into categories (name) values (?)", "x".repeat(121)))
                .as("the 120-char cap is a CHECK, not only an @Size")
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() ->
                jdbc.update("insert into categories (name) values (?)", ""))
                .as("the 1-char floor is the same CHECK")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<String> appliedVersions() {
        return Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .map(v -> v == null ? null : v.toString())
                .toList();
    }

    private List<String> pendingVersions() {
        return Arrays.stream(flyway.info().pending())
                .map(MigrationInfo::getVersion)
                .map(v -> v == null ? null : v.toString())
                .toList();
    }
}
