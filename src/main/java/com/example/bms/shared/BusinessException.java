package com.example.bms.shared;

import org.springframework.http.HttpStatus;

/**
 * A refusal the business itself makes (architecture §6: the domain throws,
 * the advice maps). Carries the status and the sentence — the advice turns
 * it into the RFC 9457 body, so a rule lives in one place instead of being
 * re-decided per endpoint.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String detail;

    public BusinessException(HttpStatus status, String detail) {
        super(detail);
        this.status = status;
        this.detail = detail;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }
}
