package com.example.bms.product;

/**
 * A category row (GLOSSARY Category): an ADMIN-managed grouping of products,
 * business data rather than code. Immutable — a rename or a status change
 * produces a new instance, so the service hands out values nobody can mutate
 * behind its back.
 *
 * <p>The name is canonicalized to its trimmed form (GLOSSARY "Canonical
 * form"): the unique index is on {@code lower(name)}, so trailing spaces
 * would otherwise invent a second row a person reads as the same word — and
 * pad past #41's length cap unnoticed. Casing is kept: a name is display
 * data ("Padaria" stays "Padaria"); identity comes from the index.
 */
public record Category(long id, String name, boolean active) {

    public Category {
        name = name.trim();
    }

    public Category renamedTo(String name) {
        return new Category(id, name, active);
    }

    /** The status flip (api-design: PUT /{resource}/{id}/status with {active}). */
    public Category withActive(boolean active) {
        return new Category(id, name, active);
    }

    /**
     * The insert path: same row, now carrying the identity column the
     * database assigned — sibling of {@link #withActive}, so the
     * repository never re-lists the fields by hand.
     */
    public Category withId(long id) {
        return new Category(id, name, active);
    }
}
