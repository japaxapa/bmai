package com.example.bms.supplier;

/**
 * A supplier row (GLOSSARY Supplier): a business that sells products to us for
 * resale — the counterpart of the customer on the buy side. Immutable, like
 * {@code Category} and {@code Product}: an edit or a status flip produces a new
 * instance, so the service hands out values nobody can mutate behind its back.
 *
 * <p>{@code document} is the CPF/CNPJ-style identity of the party (requirements:
 * "CPF/CNPJ-style {@code document}") and it is stored in canonical form
 * (GLOSSARY "Canonical form"): digits only. The punctuation a person types is
 * presentation — {@code 11.222.333/0001-81} and {@code 11222333000181} are one
 * spelling to the unique index and to a person reading them.
 *
 * <p>The check-digit judgement runs in the constructor — the invariant's home
 * (ADR 0017) — but the modulus-11 arithmetic itself lives in
 * {@link CheckDigits}, the one copy of the rule the fixture generators share.
 */
public record Supplier(
        long id,
        String name,
        String document,
        String email,
        int paymentTermsDays,
        boolean active) {

    public Supplier {
        name = name.trim();
        document = canonicalDocument(document);
        if (document.length() != 11 && document.length() != 14) {
            throw new InvalidSupplierException(
                    "Document must be 11 digits (CPF) or 14 digits (CNPJ).");
        }
        if (!CheckDigits.matches(document)) {
            throw new InvalidSupplierException("Document check digits do not match.");
        }
    }

    /**
     * The canonical form of a document: the digits, nothing else. Never a
     * locale-sensitive transform (the digits are the same in every locale, and
     * {@code lower()} on a CPF would be meaningless anyway) — this is the
     * trimming half of "Canonical form", applied to a field whose only
     * readable spelling is the digit string itself.
     */
    private static String canonicalDocument(String document) {
        return document.replaceAll("\\D", "");
    }

    /** The status flip (api-design: PUT /{resource}/{id}/status with {active}). */
    public Supplier withActive(boolean active) {
        return new Supplier(id, name, document, email, paymentTermsDays, active);
    }

    /**
     * The insert path: same row, now carrying the identity column the
     * database assigned — so the repository never re-lists the fields by hand.
     */
    public Supplier withId(long id) {
        return new Supplier(id, name, document, email, paymentTermsDays, active);
    }
}
