package com.example.bms.shared;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;

/**
 * Builds the RFC 9457 bodies this API answers with.
 *
 * Spring Security rejects in the filter chain (never reaching
 * {@code @RestControllerAdvice}), so the same shape has to be constructible
 * from both sides of that boundary: {@link ProblemDetailAuthenticationEntryPoint}
 * for security failures and controllers for domain-refused requests.
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail unauthorized(String detail, String requestUri) {
        return of(HttpStatus.UNAUTHORIZED, detail, requestUri);
    }

    /**
     * One construction for every status, so the shape (type/title/status/
     * detail/instance) cannot drift between the security side and the
     * advice side of the boundary.
     */
    public static ProblemDetail of(HttpStatus status, String detail, String requestUri) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(requestUri));
        return problem;
    }
}
