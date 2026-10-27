package com.example.bms.order;

import com.example.bms.shared.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * A sales order the domain itself refuses — a quantity that is not a
 * quantity, a deactivated party or product, a product listed twice. These are
 * business rules rather than DTO shape, so the advice layer answers 422
 * (api-design errors table), with a sentence the form or toast can print.
 */
public class InvalidOrderException extends BusinessException {

    public InvalidOrderException(String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }
}
