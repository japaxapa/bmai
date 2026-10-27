package com.example.bms.order;

import com.example.bms.shared.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * A sales order that is no longer a draft was asked to change: stock and
 * money may already hang off it, so the state machine refuses (business-rules
 * §1: "DRAFT: freely editable", every later state not). 409, not 422 — the
 * row exists and conflicts with the request (api-design errors table), the
 * same status the cancel-PROCESSING and insufficient-stock refusals carry.
 */
public class OrderNotEditableException extends BusinessException {

    public OrderNotEditableException() {
        super(HttpStatus.CONFLICT, "Only DRAFT sales orders can be edited.");
    }
}
