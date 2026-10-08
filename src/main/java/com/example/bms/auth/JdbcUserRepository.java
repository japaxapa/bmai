package com.example.bms.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JdbcUserRepository implements UserRepository {

    private final JdbcTemplate jdbc;

    public JdbcUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        // Emails are stored lowercase (V2 constraint), so the lookup normalises
        // the other half of the comparison: login must not depend on case.
        // The active flag is part of the same predicate (database-design: a
        // deactivated user's credentials stop answering — deactivation is how
        // this system revokes access, since users are never deleted).
        var rows = jdbc.query(
                "select email, password_hash, role from users where email = ? and active",
                (rs, rowNum) -> new User(
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        Role.valueOf(rs.getString("role"))),
                email == null ? null : email.toLowerCase());
        return rows.stream().findFirst();
    }
}
