package com.example.bms.product;

import com.example.bms.shared.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * api-design errors table: "404 | unknown id". A stale row (edited in
 * another tab) must not become a 500 or an empty 404 — the UI toasts the
 * same envelope it gets everywhere else.
 */
public class ProductNotFoundException extends BusinessException {

    public ProductNotFoundException(long id) {
        super(HttpStatus.NOT_FOUND, "No product with id " + id);
    }
}
