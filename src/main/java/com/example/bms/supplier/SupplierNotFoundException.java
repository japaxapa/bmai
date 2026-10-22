package com.example.bms.supplier;

import com.example.bms.shared.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * api-design errors table: "404 | unknown id". A stale row (edited in
 * another tab) must not become a 500 or an empty 404 — the UI toasts the
 * same envelope it gets everywhere else.
 */
public class SupplierNotFoundException extends BusinessException {

    public SupplierNotFoundException(long id) {
        super(HttpStatus.NOT_FOUND, "No supplier with id " + id);
    }
}
