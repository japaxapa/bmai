package com.example.bms.shared;

import java.util.Locale;

/**
 * The stored spelling of an identifier (GLOSSARY "Canonical form"), in one
 * place so the fold can never drift between writers: {@code Product}'s SKU,
 * {@code JdbcUserRepository}'s login lookup and {@code AdminSeeder}'s
 * configured ADMIN_EMAIL each carried their own copy of this expression
 * and their own copy of the reason for it — which is how the email path
 * kept the no-arg form after R12 (03bd0d1) fixed the SKU, the exact
 * drift this class exists to make impossible (one rule, three callers,
 * the same seam {@link CheckDigits} and {@link LikeTerm} became for their
 * rules).
 *
 * <p>{@link Locale#ROOT} on purpose: the no-arg {@code toLowerCase()} reads
 * the JVM default locale, which under tr-TR folds "I" into a dotless ı —
 * while Postgres {@code lower()} folds it to "i", so the stored value and
 * the database's CHECK/unique index would disagree about what the row even
 * is (03bd0d1 pinned the SKU half against the real stack, #48 the email
 * half; the tr-TR simulation tests run in both seams).
 *
 * <p>It lives in {@code shared} because the rule belongs to no single
 * feature: the catalog and the auth writers are two faces of the same
 * glossary concept, and neither may import the other's internals
 * (ADR 0004). Only the lowercased flavour comes here — a category name is
 * trimmed but display data (its casing stays), and a document is stripped
 * to digits; those halves of the same glossary entry keep their own code.
 */
public final class CanonicalForm {

    private CanonicalForm() {
    }

    /**
     * The identifier flavour of the canonical form: trimmed of surrounding
     * whitespace and lowercased with {@link Locale#ROOT} — so {@code "CAFE-1"},
     * {@code "cafe-1"} and {@code " CAFE-1 "} are one value, whatever the
     * deployment's LANG is. The counterpart of the database's own
     * {@code lower(btrim(...))}, which is what the parity tests compare
     * against.
     *
     * @param value the spelling as typed; must not be {@code null}
     * @return the stored spelling
     */
    public static String lowercaseTrimmed(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
