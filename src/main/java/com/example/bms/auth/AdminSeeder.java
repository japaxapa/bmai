package com.example.bms.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Applies ADMIN_EMAIL / ADMIN_PASSWORD to the seeded admin (requirements §2:
 * "First ADMIN is seeded from env vars").
 *
 * Why this exists: V2 seeds a fixed bcrypt hash so a fresh clone is demoable
 * with zero configuration, and SQL cannot bcrypt an env var. This runs once at
 * boot and rewrites that row instead — so wherever the variables are set, the
 * documented default credential stops existing rather than sitting next to the
 * configured one.
 *
 * It is a no-op with nothing configured, and a no-op once the configured
 * values are already in place, so restarts never fight a password the admin
 * later changed through the application.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final String configuredEmail;
    private final String configuredPassword;

    public AdminSeeder(JdbcTemplate jdbc,
                       PasswordEncoder passwordEncoder,
                       @Value("${ADMIN_EMAIL:}") String configuredEmail,
                       @Value("${ADMIN_PASSWORD:}") String configuredPassword) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.configuredEmail = configuredEmail;
        this.configuredPassword = configuredPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (configuredEmail.isBlank() && configuredPassword.isBlank()) {
            return;
        }

        var admins = jdbc.query(
                "select id, email, password_hash from users where role = 'ADMIN'",
                (rs, rowNum) -> new SeededAdmin(
                        rs.getLong("id"), rs.getString("email"), rs.getString("password_hash")));
        if (admins.size() != 1) {
            // More than one ADMIN means user management has been in use and the
            // seed row can no longer be told apart: refuse to guess.
            log.warn("ADMIN_EMAIL/ADMIN_PASSWORD ignored: expected the single seeded ADMIN, found {}", admins.size());
            return;
        }

        var admin = admins.get(0);
        var email = configuredEmail.isBlank() ? admin.email() : configuredEmail.trim().toLowerCase();
        var passwordHash = configuredPassword.isBlank() || passwordEncoder.matches(configuredPassword, admin.passwordHash())
                ? admin.passwordHash()
                : passwordEncoder.encode(configuredPassword);

        if (email.equals(admin.email()) && passwordHash.equals(admin.passwordHash())) {
            return;
        }
        jdbc.update("update users set email = ?, password_hash = ? where id = ?", email, passwordHash, admin.id());
        log.info("Seeded admin now answers to the configured ADMIN_EMAIL/ADMIN_PASSWORD");
    }

    private record SeededAdmin(long id, String email, String passwordHash) {
    }
}
