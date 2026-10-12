package com.example.bms.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one {@code @RestControllerAdvice} api-design promises: every domain or
 * DTO refusal leaves as an RFC 9457 body with the same shape the security
 * side already answers with (see {@link ProblemDetailAuthenticationEntryPoint}
 * for the 401, which never reaches this class).
 *
 * <p>The detail sentences are fixed rather than taken from framework
 * exceptions: Hibernate/Spring wording changes between versions and must not
 * leak into the API contract — except where the message is *about the caller's
 * field*, which is exactly what {@code fieldErrors} carries back.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * AC: "Validation errors show field-level messages in the UI" — which is
     * only possible if the answer says *which field* refused. The extension
     * maps a field name to its messages: {@code {"fieldErrors": {"name": [...]}}}.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validationRefused(MethodArgumentNotValidException exception,
                                            HttpServletRequest request) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.computeIfAbsent(error.getField(), field -> new ArrayList<>())
                        .add(error.getDefaultMessage()));

        var problem = ProblemDetails.of(
                HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI());
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    /**
     * A malformed body is the caller's mistake, not a server fault: without
     * this it leaves as a bare 400 (or a 500 through the error page) with no
     * sentence for the UI to show.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadableBody(HttpMessageNotReadableException exception,
                                         HttpServletRequest request) {
        return ProblemDetails.of(
                HttpStatus.BAD_REQUEST, "The request body could not be read", request.getRequestURI());
    }

    /**
     * The domain's own refusals (architecture §6): the exception carries the
     * status and the sentence, this one puts them in the shared envelope —
     * 404 for an unknown id today, 409 conflicts when they arrive.
     */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail domainRefused(BusinessException exception, HttpServletRequest request) {
        return ProblemDetails.of(
                exception.getStatus(), exception.getDetail(), request.getRequestURI());
    }

    /**
     * Spring's default {@code AccessDeniedHandler} answers an <em>empty</em>
     * 403 — a refusal the frontend can only render as a bare status line.
     * This one carries the sentence the toast needs. It is only ever reached
     * from inside the dispatcher (method security on an authenticated call):
     * filter-chain refusals never come this far, and an unauthenticated
     * caller is answered 401 by the entry point before a role is considered.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail accessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return ProblemDetails.of(HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action", request.getRequestURI());
    }
}
