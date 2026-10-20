package com.example.bms.supplier;

import com.example.bms.shared.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * A supplier the domain itself refuses — a bad document, not a bad shape:
 * neither a CPF (11 digits) nor a CNPJ (14 digits), or check digits that do
 * not add up (api-design errors table: 422 "business rule refuses"). The
 * guard lives on the entity (ADR 0017), so the refusal cannot depend on a
 * service remembering to check — and this exception is only the translation
 * into the RFC 9457 sentence the UI shows inline.
 */
public class InvalidSupplierException extends BusinessException {

    public InvalidSupplierException(String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }
}
