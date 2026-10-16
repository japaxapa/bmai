package com.example.bms.product;

import com.example.bms.shared.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * A product the domain itself refuses — a bad range, not a bad shape:
 * price ≤ 0, cost &lt; 0 or minimum stock &lt; 0 (api-design errors table,
 * whose 422 example is literally "cost &lt; 0"). The guard lives on the
 * entity (ADR 0017), so the refusal cannot depend on a service remembering
 * to check — and this exception is only the translation into the RFC 9457
 * sentence the UI shows.
 */
public class InvalidProductException extends BusinessException {

    public InvalidProductException(String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }
}
