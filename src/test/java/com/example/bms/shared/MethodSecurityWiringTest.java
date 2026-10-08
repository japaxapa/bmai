package com.example.bms.shared;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Seam 4 (agreed with the user): method-security wiring.
 *
 * The probe is test-scoped on purpose — ticket #5 is where production
 * {@code @PreAuthorize} rules land (categories, ADMIN-only), and inventing one
 * here would put an unagreed rule in front of the real enforcement question.
 * All this needs to prove is that the annotation is not silently inert: with
 * {@code @EnableMethodSecurity} absent the EMPLOYEE call answers, which is the
 * red; with it present the call is refused, which is the green.
 *
 * Real context, real filter chain, real PostgreSQL (testing-strategy §3) —
 * never a mock of the security infrastructure under test.
 */
@Testcontainers
@SpringBootTest
class MethodSecurityWiringTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private GuardedProbe probe;

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCallIsDenied() {
        assertThatThrownBy(probe::adminOnly)
                .as("method security must be live, not just declared")
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCallSucceeds() {
        assertThat(probe.adminOnly())
                .as("the guard must refuse on role, not on everybody")
                .isEqualTo("allowed");
    }

    /** Stands in for the ADMIN-only services #5 will add. */
    public static class GuardedProbe {

        @PreAuthorize("hasRole('ADMIN')")
        public String adminOnly() {
            return "allowed";
        }
    }

    @TestConfiguration
    static class ProbeConfiguration {

        @Bean
        GuardedProbe guardedProbe() {
            return new GuardedProbe();
        }
    }
}
