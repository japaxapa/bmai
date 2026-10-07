package com.example.bms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seam 1 (agreed with the user): the HTTP health endpoint.
 *
 * Observed exactly as a caller would: a real HTTP GET against a running
 * server, asserting only on what comes back over the wire. The server runs
 * against a real PostgreSQL container, so "healthy" includes the database.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class HealthEndpointTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private TestRestTemplate rest;

    @Test
    void healthEndpointReportsUpIncludingDatabase() {
        var response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("health endpoint should answer with a success status")
                .isTrue();
        assertThat(response.getBody())
                .as("health body should report overall UP")
                .contains("\"status\":\"UP\"");
        assertThat(response.getBody())
                .as("health body should report the db component as UP")
                .contains("\"db\":{\"status\":\"UP\"}");
    }
}
