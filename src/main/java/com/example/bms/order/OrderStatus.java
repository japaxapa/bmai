package com.example.bms.order;

/**
 * The lifecycle of a sales order (GLOSSARY "Sales order"): DRAFT → CONFIRMED
 * → PROCESSING → COMPLETED, with CANCELLED from DRAFT/CONFIRMED and RETURNED
 * from COMPLETED. Only DRAFT is editable; the transitions themselves arrive
 * with ticket #10, so this enum names the states without owning the moves.
 */
public enum OrderStatus {
    DRAFT,
    CONFIRMED,
    PROCESSING,
    COMPLETED,
    CANCELLED,
    RETURNED
}
