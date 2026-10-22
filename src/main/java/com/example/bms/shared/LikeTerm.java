package com.example.bms.shared;

/**
 * The ILIKE term for a live search, in one place: wildcards the user typed
 * are data, not syntax — '%' in a search box must find a literal '%', not
 * every row. Blank (or absent) means no filter ({@code null}, which SQL
 * reads as "no WHERE clause on this column").
 *
 * <p>One rule, three callers ({@code JdbcProductRepository},
 * {@code JdbcSupplierRepository}, {@code JdbcCustomerRepository}): the
 * escape order below is load-bearing ({@code \} first, then {@code %},
 * then {@code _} — reversed, the backslashes double-escape), and it only
 * pairs with search predicates written as {@code ilike ? escape '\'}.
 * Two copies of that pair were already maintained by comment alone; this
 * class is the single copy they both call (the same seam {@link CheckDigits}
 * became for the CPF/CNPJ arithmetic).
 */
public final class LikeTerm {

    private LikeTerm() {
    }

    /**
     * @param value the raw term from the request; {@code null} or blank is
     *              "no filter" and comes back as {@code null}
     * @return {@code value} wrapped in {@code %} with LIKE's three
     *         wildcards escaped, ready for one {@code ilike ?} placeholder
     */
    public static String of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "%" + value.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }
}
