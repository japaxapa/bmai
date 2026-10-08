package com.example.bms.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seam B (agreed with the user): the whole auth stack against a real
 * PostgreSQL — real V2 migration, real BCrypt, real repository, real filter
 * chain — observed only over HTTP.
 *
 * Requirements §2: the first ADMIN is seeded from ADMIN_EMAIL / ADMIN_PASSWORD.
 * Because a SQL migration cannot bcrypt an env var, the fixed row from V2 is
 * rewritten at boot, so the documented default credential cannot survive next
 * to a configured one.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ADMIN_EMAIL=owner@bms.local",
                "ADMIN_PASSWORD=from-the-env-9876"
        })
@AutoConfigureTestRestTemplate
class AdminSeederTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private TestRestTemplate rest;

    @Test
    void configuredCredentialsReplaceTheSeededOnes() {
        assertThat(login("owner@bms.local", "from-the-env-9876").getStatusCode().is2xxSuccessful())
                .as("the configured account is the one that answers")
                .isTrue();

        assertThat(login("admin@bms.local", "admin123").getStatusCode().value())
                .as("the documented default must no longer authenticate")
                .isEqualTo(401);
    }

    private ResponseEntity<String> login(String email, String password) {
        return rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", password)),
                String.class);
    }
}
