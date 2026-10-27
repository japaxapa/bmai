package com.example.bms.customer;

import com.example.bms.shared.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * A document somebody else already holds. The refusal itself comes from the
 * database (V7's unique index on {@code document}) — this exception is only
 * the translation into the sentence the UI renders inline, so it cannot
 * depend on a service remembering to check first (and an edit cannot race
 * past that check either).
 */
public class CustomerConflictException extends BusinessException {

    public CustomerConflictException(String document) {
        super(HttpStatus.CONFLICT,
                "A customer with document " + document + " already exists.");
    }
}
