package com.example.bms.product;

import com.example.bms.shared.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * A name somebody else already holds. The refusal itself comes from the
 * database (V3's unique index on {@code lower(name)}) — this exception is
 * only the translation into the sentence the UI renders inline, so it cannot
 * depend on a service remembering to check first.
 */
public class CategoryConflictException extends BusinessException {

    public CategoryConflictException(String name) {
        super(HttpStatus.CONFLICT, "A category named " + name + " already exists.");
    }
}
