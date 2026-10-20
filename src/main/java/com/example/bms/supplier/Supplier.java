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
        if (!hasMatchingCheckDigits(document)) {
            throw new InvalidSupplierException("Document check digits do not match.");
        }
    }

    /**
     * The CPF/CNPJ verification digits (GLOSSARY's CPF/CNPJ-style document).
     * Both are modulus-11: a weighted sum of the digits, {@code r = sum % 11},
     * and the digit is {@code r < 2 ? 0 : 11 - r} — CPF over its two windows
     * (weights 10→2, then 11→2), CNPJ over its two fixed tables (5,4,3,2,
     * 9,8,7,6,5,4,3,2 then 6,5,4,3,2,9,8,7,6,5,4,3,2).
     *
     * <p>Deliberately pure code on the entity (ADR 0017) rather than a
     * database CHECK: a checksum is arithmetic, not a constraint Postgres can
     * express without a function — and every write path funnels through this
     * constructor anyway, so nothing can skip it by skipping the service.
     */
    private static boolean hasMatchingCheckDigits(String digits) {
        return digits.length() == 11
                ? cpfCheckDigitsMatch(digits)
                : cnpjCheckDigitsMatch(digits);
    }

    private static boolean cpfCheckDigitsMatch(String digits) {
        var first = checkDigit(digits, 9, index -> 10 - index);
        if (first != digits.charAt(9) - '0') {
            return false;
        }
        var second = checkDigit(digits, 10, index -> 11 - index);
        return second == digits.charAt(10) - '0';
    }

    private static boolean cnpjCheckDigitsMatch(String digits) {
        var firstWeights = new int[] {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        var first = checkDigit(digits, 12, index -> firstWeights[index]);
        if (first != digits.charAt(12) - '0') {
            return false;
        }
        var secondWeights = new int[] {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        var second = checkDigit(digits, 13, index -> secondWeights[index]);
        return second == digits.charAt(13) - '0';
    }

    /** The modulus-11 digit for the first {@code length} digits of {@code digits}. */
    private static int checkDigit(String digits, int length,
                                  java.util.function.IntUnaryOperator weight) {
        var sum = 0;
        for (var index = 0; index < length; index++) {
            sum += (digits.charAt(index) - '0') * weight.applyAsInt(index);
        }
        var remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
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
