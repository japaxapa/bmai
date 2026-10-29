package com.example.bms.shared;

import java.util.regex.Pattern;

/**
 * A server-minted document number in the {@code PREFIX-YYYY-NNNN} shape
 * (api-design: "Numbers {@code ORD|PO|INV-YYYY-NNNN} generated
 * server-side"). The format lives here rather than in each document feature
 * because it is one rule worn by several documents: ticket #9 mints
 * {@code ORD} for sales orders, and purchase orders and invoices reuse this
 * type when they arrive — the prefix is a field, not "ORD" baked in. Like
 * {@link CheckDigits}, it sits in {@code shared} so no document feature may
 * import another's internals (ADR 0004).
 *
 * <p>Immutable and value-based: two numbers with the same prefix, year and
 * sequence are the same number, and {@link #value()} is the canonical string
 * — what the {@code orders.number} column stores and the API answers. The
 * sequence is zero-padded to four digits ({@code ORD-2026-0007}) but may
 * grow past them ({@code ORD-2026-12345}), matching the V8 CHECK
 * {@code '^ORD-[0-9]{4}-[0-9]{4,}$'} that backstops it in the database.
 *
 * <p>The server is the only minter: a client-supplied number never reaches
 * this type, so a value that fails {@link #parse} is a stored row contradicting
 * its own format — a server bug, not a user error — which is why
 * {@code parse} throws rather than returning empty.
 */
public record DocumentNumber(String prefix, int year, long sequence) {

    /** The shape every stored number must match: {@code PREFIX-YYYY-NNNN…}. */
    private static final Pattern SHAPE = Pattern.compile("([A-Z]+)-(\\d{4})-(\\d{4,})");

    /** A prefix is uppercase letters only: {@code ORD}, {@code PO}, {@code INV}. */
    private static final Pattern PREFIX = Pattern.compile("[A-Z]+");

    public DocumentNumber {
        if (prefix == null || !PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException("Document prefix must be uppercase letters: " + prefix);
        }
        if (year < 0 || year > 9999) {
            throw new IllegalArgumentException("Document year must fit YYYY: " + year);
        }
        if (sequence < 1) {
            throw new IllegalArgumentException("Document sequence must be positive: " + sequence);
        }
    }

    /**
     * The number a document feature asks for on insert: the prefix it owns,
     * the year the mint happens in (UTC, so a date line and its number can
     * never disagree about what day it is), and the next value of the
     * database sequence. The year is an argument rather than read from the
     * clock here so the rule stays pure — the caller states the moment.
     *
     * @param prefix   the feature's prefix, e.g. {@code "ORD"}
     * @param year     the mint year, e.g. {@code 2026}
     * @param sequence the database sequence's next value (starts at 1)
     */
    public static DocumentNumber mint(String prefix, int year, long sequence) {
        return new DocumentNumber(prefix, year, sequence);
    }

    /**
     * The stored string read back into a number. The server is the only
     * minter, so anything that does not match the shape contradicts the
     * format this class owns — fail loudly rather than carry a malformed
     * value through the domain as if it were one.
     *
     * @param value the canonical {@code PREFIX-YYYY-NNNN} string
     * @throws IllegalArgumentException if the value is not that shape
     */
    public static DocumentNumber parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Document number must not be null.");
        }
        var shape = SHAPE.matcher(value);
        if (!shape.matches()) {
            throw new IllegalArgumentException("Not a document number: " + value);
        }
        return new DocumentNumber(shape.group(1),
                Integer.parseInt(shape.group(2)), Long.parseLong(shape.group(3)));
    }

    /**
     * The canonical string — the same value {@link #toString()} answers, so
     * a log line and a JSON field read alike. Call it at the API boundary
     * (and before binding it to SQL); the domain itself keeps the parts.
     */
    public String value() {
        return "%s-%04d-%04d".formatted(prefix, year, sequence);
    }

    @Override
    public String toString() {
        return value();
    }
}
