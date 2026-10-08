package com.example.bms.shared;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

import tools.jackson.databind.ObjectMapper;

/**
 * Turns Spring Security's 401s into RFC 9457 bodies.
 *
 * Security failures never reach {@code @RestControllerAdvice} — they are
 * rejected in the filter chain — so the problem+json shape for 401 comes from
 * here. Same shape as the advice: {@code type}/{@code title}/{@code status}/
 * {@code detail}/{@code instance}.
 *
 * The detail is fixed rather than taken from the exception: framework wording
 * changes between versions and must not leak into the API contract.
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authenticationException) throws IOException {
        // Two ways to arrive here: no credential at all, or one we would not
        // have issued (bad signature, expired). Chosen by exception type, never
        // by message: framework wording changes and must not leak into the API.
        // Neither case says anything about which users exist.
        var detail = authenticationException instanceof InvalidBearerTokenException
                ? "Invalid or expired token"
                : "Authentication is required";
        var problem = ProblemDetails.unauthorized(detail, request.getRequestURI());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
