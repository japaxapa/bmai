package com.example.bms.customer;

/**
 * The slice of a customer another feature needs to put it on a document: the
 * id to store, the name to display and the flag that decides whether it may
 * be used at all (business-rules §5). A narrow read like
 * {@code SupplierService.findName} — the order feature reaches it through
 * {@link CustomerService}, never the repository or the entity
 * (architecture §1).
 */
public record CustomerRef(long id, String name, boolean active) {
}
