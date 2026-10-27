package com.example.bms.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Optional;

@Repository
public class JdbcUserRepository implements UserRepository {

    private final JdbcTemplate jdbc;

    public JdbcUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        // Emails are stored canonical (V2 constraint), so the lookup applies
        // the same form to the posted spelling: trimmed and lowercased, so
        // " ADMIN@BMS.LOCAL " and "admin@bms.local" are one login (GLOSSARY
        // "Canonical form"). Lowercasing is Locale.ROOT on purpose: the
        // no-arg form reads the JVM default locale, which under tr-TR turns
        // "I" into a dotless ı while Postgres lower() folds it to "i" —
        // a correct password would then miss the row and answer 401.
        // The active flag is part of the same predicate (database-design: a
        // deactivated user's credentials stop answering — deactivation is how
        // this system revokes access, since users are never deleted).
        var rows = jdbc.query(
                "select email, password_hash, role from users where email = ? and active",
                (rs, rowNum) -> new User(
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        Role.valueOf(rs.getString("role"))),
                email == null ? null : email.trim().toLowerCase(Locale.ROOT));
        return rows.stream().findFirst();
    }
}
