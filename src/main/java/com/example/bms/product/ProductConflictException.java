package com.example.bms.product;

import com.example.bms.shared.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * A SKU somebody else already holds. The refusal itself comes from the
 * database (V4's unique index on {@code lower(sku)}) — this exception is
 * only the translation into the sentence the UI renders inline, so it
 * cannot depend on a service remembering to check first (and a rename
 * cannot race past that check either).
 */
public class ProductConflictException extends BusinessException {

    public ProductConflictException(String sku) {
        super(HttpStatus.CONFLICT, "A product with SKU " + sku + " already exists.");
    }
}
