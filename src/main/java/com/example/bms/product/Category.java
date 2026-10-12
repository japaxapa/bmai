package com.example.bms.product;

/**
 * A category row (GLOSSARY Category): an ADMIN-managed grouping of products,
 * business data rather than code. Immutable — a rename or a status change
 * produces a new instance, so the service hands out values nobody can mutate
 * behind its back.
 */
public record Category(long id, String name, boolean active) {

    public Category renamedTo(String name) {
        return new Category(id, name, active);
    }

    /** The status flip (api-design: PUT /{resource}/{id}/status with {active}). */
    public Category withActive(boolean active) {
        return new Category(id, name, active);
    }
}
