package com.example.bms.order;

import com.example.bms.shared.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * api-design errors table: "404 | unknown id". A stale link or a row edited
 * in another tab must answer the shared problem+json envelope, not a 500.
 */
public class OrderNotFoundException extends BusinessException {

    public OrderNotFoundException(long id) {
        super(HttpStatus.NOT_FOUND, "No order with id " + id);
    }
}
