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
import static org.assertj.core.api.Assertions.assertThatCode;
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

    /**
     * V5 (GLOSSARY "Canonical form"): a unique text field is stored trimmed —
     * and lowercased where it is an identifier (SKU). The entities
     * canonicalize on the way in; these CHECKs are the backstop for a writer
     * that is not the API (console, future import), the same layering V4
     * gave the range checks.
     */
    @Test
    void theCanonicalFormChecksBiteAtTheDatabaseLevel() {
        var category = jdbc.queryForObject(
                "insert into categories (name) values ('Canonical-Check') returning id",
                Long.class);

        assertThatThrownBy(() -> jdbc.update(
                "insert into categories (name) values (?)", " Padded "))
                .as("a name is stored trimmed — padding would invent a second row "
                        + "the lower(name) index sees as distinct")
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                "insert into products (sku, name, price, category_id) values (?, ?, ?, ?)",
                "CAFE-PAD ", "Com espaço", 10, category))
                .as("a SKU is stored trimmed and lowercase")
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                "insert into products (sku, name, price, category_id) values (?, ?, ?, ?)",
                "CAFE-UPPER", "Caixa alta", 10, category))
                .as("the lowercase half of the same CHECK — the index's spelling is the only spelling")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Ticket 7, AC "Constraint violations return 409/422": the unique
     * {@code document} index refuses the duplicate at the database, so it
     * cannot depend on a service remembering to check first.
     *
     * <p>Unlike the SKU and category-name indexes this one is exact, not
     * lower()-based: a CPF/CNPJ is digits, so there is no case to fold —
     * and no spelling for the index to collapse either. The two spellings
     * collide *before* the index sees them, because the entity strips the
     * punctuation (the row is stored as digits); what is asserted here is
     * that the index then refuses the second row on the digits alone.
     */
    @Test
    void theUniqueSupplierDocumentIndexBitesAtTheDatabaseLevel() {
        jdbc.update("""
                        insert into suppliers (name, document)
                        values (?, ?)
                        """, "Torrefação Aurora LTDA", "12345678000195");

        assertThatThrownBy(() -> jdbc.update("""
                        insert into suppliers (name, document)
                        values (?, ?)
                        """, "Aurora duplicada", "12345678000195"))
                .as("the index refuses the second row on the digits alone")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * V6's length CHECK: 11 digits is a CPF, 14 a CNPJ, anything else is not
     * a document. The check *digits* stay on the entity (ADR 0017) — a
     * modulus-11 checksum is arithmetic, not a CHECK — so this is the shape
     * half, and the backstop for a writer that is not the API.
     */
    @Test
    void theSupplierDocumentLengthCheckBitesAtTheDatabaseLevel() {
        for (var length : new int[] {10, 12, 13, 15}) {
            assertThatThrownBy(() -> jdbc.update(
                            "insert into suppliers (name, document) values (?, ?)",
                            "Documento torto", "1".repeat(length)))
                    .as("%d digits is neither a CPF nor a CNPJ".formatted(length))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    /**
     * Ticket 7's other half: a product may name its supplier, and the FK
     * refuses a supplier that does not exist. RESTRICT (never CASCADE) is
     * the same choice category_id made — retiring a party is a status flip,
     * so history can never lose the row it points at.
     */
    @Test
    void aProductMayNameItsSupplierAndTheForeignKeyRefusesAnUnknownOne() {
        // SKUs are canonical form (V5): trimmed and lowercase. These inserts
        // bypass the entity that would have canonicalized them, so the
        // fixtures spell them the way the CHECK demands — otherwise this test
        // would be refusing the spelling, not the foreign key.
        var category = jdbc.queryForObject(
                "insert into categories (name) values ('Fornecedores FK') returning id",
                Long.class);
        var supplier = jdbc.queryForObject(
                "insert into suppliers (name, document) values ('Aurora FK', '11111111000191') returning id",
                Long.class);

        assertThatCode(() -> jdbc.update("""
                        insert into products (sku, name, price, category_id, supplier_id)
                        values (?, ?, ?, ?, ?)
                        """, "cafe-fk", "Com fornecedor", 10, category, supplier))
                .as("the column accepts a supplier that exists")
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> jdbc.update("""
                        insert into products (sku, name, price, category_id, supplier_id)
                        values (?, ?, ?, ?, ?)
                        """, "cafe-fk-orfao", "Fornecedor inventado", 10, category, 999_999L))
                .as("and refuses one that does not")
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
