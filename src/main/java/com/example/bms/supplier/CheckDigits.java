package com.example.bms.supplier;

import java.util.function.IntUnaryOperator;

/**
 * The CPF/CNPJ verification-digit rule (GLOSSARY "CPF/CNPJ-style document"),
 * in one place so the side that <em>judges</em> and the side that
 * <em>generates</em> fixtures can never drift apart — "one rule, both sides"
 * as a structural fact rather than a comment about two copies staying in sync.
 *
 * <p>Both documents are modulus-11: a weighted sum of the digits,
 * {@code r = sum % 11}, and the digit is {@code r < 2 ? 0 : 11 - r} — CPF over
 * its two windows (weights 10→2, then 11→2), CNPJ over its two fixed tables
 * (5,4,3,2, 9,8,7,6,5,4,3,2 then 6,5,4,3,2,9,8,7,6,5,4,3,2).
 *
 * <p>Deliberately pure code, not a database CHECK: a checksum is arithmetic,
 * not a constraint Postgres can express without a function — and the judgement
 * still runs on {@link Supplier}'s constructor (ADR 0017), so every write path
 * funnels through it. This class is the arithmetic; the entity owns what
 * happens when the arithmetic says no.
 *
 * <p>The generator half exists because a fixture needs a <em>fresh</em> valid
 * document per invocation (V6's unique index refuses reuse) and a hardcoded
 * table would go stale the moment a test wanted a second supplier. It is not
 * a production write path: production never invents a party's document — the
 * generator serves the tests, which is why this class is the seam they share.
 */
public final class CheckDigits {

    /** CNPJ first check digit, over the 12-digit body. */
    private static final int[] CNPJ_FIRST_WEIGHTS = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    /** CNPJ second check digit, over body + first digit (13 digits). */
    private static final int[] CNPJ_SECOND_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private CheckDigits() {
    }

    /**
     * Whether the digits carry the check digits their body implies: 11 digits
     * are judged as CPF, 14 as CNPJ, any other length is simply not a valid
     * document (the caller states which length it expected, if it cares —
     * {@link Supplier}'s constructor answers a distinct sentence for length).
     */
    public static boolean matches(String digits) {
        return switch (digits.length()) {
            case 11 -> cpfMatches(digits);
            case 14 -> cnpjMatches(digits);
            default -> false;
        };
    }

    /**
     * The 14-digit CNPJ whose check digits follow from a 12-digit body — the
     * fixture-side counterpart of {@link #matches}: generate with the rule the
     * domain judges with, and a "valid per test" document is valid per the
     * domain by construction rather than by coincidence.
     *
     * @param body exactly 12 digits (the CNPJ root + ordering digits)
     * @return the body plus its two modulus-11 check digits
     */
    public static String cnpjWithCheckDigits(String body) {
        if (body.length() != 12) {
            throw new IllegalArgumentException("CNPJ body must be 12 digits: " + body);
        }
        var first = checkDigit(body, CNPJ_FIRST_WEIGHTS.length,
                index -> CNPJ_FIRST_WEIGHTS[index]);
        var withFirst = body + first;
        var second = checkDigit(withFirst, CNPJ_SECOND_WEIGHTS.length,
                index -> CNPJ_SECOND_WEIGHTS[index]);
        return withFirst + second;
    }

    private static boolean cpfMatches(String digits) {
        var first = checkDigit(digits, 9, index -> 10 - index);
        if (first != digits.charAt(9) - '0') {
            return false;
        }
        var second = checkDigit(digits, 10, index -> 11 - index);
        return second == digits.charAt(10) - '0';
    }

    private static boolean cnpjMatches(String digits) {
        var first = checkDigit(digits, 12, index -> CNPJ_FIRST_WEIGHTS[index]);
        if (first != digits.charAt(12) - '0') {
            return false;
        }
        var second = checkDigit(digits, 13, index -> CNPJ_SECOND_WEIGHTS[index]);
        return second == digits.charAt(13) - '0';
    }

    /** The modulus-11 digit for the first {@code length} digits of {@code digits}. */
    private static int checkDigit(String digits, int length,
                                  IntUnaryOperator weight) {
        var sum = 0;
        for (var index = 0; index < length; index++) {
            sum += (digits.charAt(index) - '0') * weight.applyAsInt(index);
        }
        var remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
