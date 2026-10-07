package com.example.bms;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
