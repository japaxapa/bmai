package com.example.bms.auth;

import java.util.Optional;

/**
 * Where auth reads users from.
 *
 * A port over the {@code users} table, so the login logic can be exercised
 * without a database (the slice tests stub exactly this boundary) and the SQL
 * lives behind one class.
 */
public interface UserRepository {

    Optional<User> findByEmail(String email);
}
