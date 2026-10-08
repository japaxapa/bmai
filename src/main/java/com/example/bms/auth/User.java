package com.example.bms.auth;

/**
 * The credentials a login attempt is checked against — the {@code users} row,
 * mapped by hand (api-design: manual DTO mapping, no MapStruct; the repository
 * is plain JDBC, no ORM). The role travels into the JWT claim.
 */
public record User(String email, String passwordHash, Role role) {
}
