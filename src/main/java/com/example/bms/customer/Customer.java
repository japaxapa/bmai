package com.example.bms.customer;

import com.example.bms.shared.CheckDigits;

/**
 * A customer row (GLOSSARY Customer): a business that buys products from us
 * on credit — the counterpart of the supplier on the sell side. Immutable,
 * like {@code Supplier}: an edit or a status flip produces a new instance,
 * so the service hands out values nobody can mutate behind its back.
 *
 * <p>{@code document} is the CPF/CNPJ-style identity of the party and it is
 * stored in canonical form (GLOSSARY "Canonical form"): digits only. The
 * punctuation a person types is presentation — {@code 11.222.333/0001-81}
 * and {@code 11222333000181} are one spelling to the unique index and to a
 * person reading them.
 *
 * <p>The judgements run in the constructor — the invariant's home (ADR 0017)
 * — each answered as the 422 {@link InvalidCustomerException} rather than
 * whatever V7 would answer: an unhandled 500 leaking SQL to the caller. The
 * document's length is refused here (a modulus-11 checksum is arithmetic,
 * not a constraint Postgres can express, so V7 only carries the shape CHECK),
 * the check digits come from the shared {@code CheckDigits} — the one copy of
 * the rule both parties judge with — and {@code paymentTermsDays} is a
 * business range (≥ 0 days, business-rules §3), same as the supplier's. The
 * CHECKs stay as the backstop for write paths that never pass through the
 * entity (seeds, raw SQL).
 */
public record Customer(
        long id,
        String name,
        String document,
        String email,
        int paymentTermsDays,
        boolean active) {

    public Customer {
        name = name.trim();
        document = canonicalDocument(document);
        if (document.length() != 11 && document.length() != 14) {
            throw new InvalidCustomerException(
                    "Document must be 11 digits (CPF) or 14 digits (CNPJ).");
        }
        if (!CheckDigits.matches(document)) {
            throw new InvalidCustomerException("Document check digits do not match.");
        }
        if (paymentTermsDays < 0) {
            throw new InvalidCustomerException("Payment terms cannot be negative.");
        }
    }

    /**
     * The canonical form of a document: the digits, nothing else (GLOSSARY
     * "Canonical form") — the same stripping {@code Supplier} applies to its
     * own copy of the field. Package-visible so the repository's search
     * applies this identical stripping rather than a copy of the regex.
     */
    static String canonicalDocument(String document) {
        return document.replaceAll("\\D", "");
    }

    /**
     * The insert path: same row, now carrying the identity column the
     * database assigned — so the repository never re-lists the fields by hand.
     */
    public Customer withId(long id) {
        return new Customer(id, name, document, email, paymentTermsDays, active);
    }

    /** The status flip (api-design: PUT /{resource}/{id}/status with {active}). */
    public Customer withActive(boolean active) {
        return new Customer(id, name, document, email, paymentTermsDays, active);
    }
}
