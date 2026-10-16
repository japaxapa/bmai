package com.example.bms.shared;

/**
 * The page-size rule, once for every collection (api-design: "size ≤ 100"
 * for every paged envelope). Both halves derive from one source: the
 * {@link #DEFAULT_PAGE_SIZE} String feeds the controllers'
 * {@code @RequestParam(defaultValue = …)} (annotations cannot take an int
 * constant), and {@link #MAX_PAGE_SIZE} is the clamp the services enforce
 * on what a caller may ask for — so the default and the ceiling can never
 * drift apart. Ticket #7 (suppliers) and #8 (customers) read the same
 * constants; the UI sends no explicit size (ADR 0019: no size picker
 * until a list asks for one).
 */
public final class PageSize {

    /** The default a caller gets by sending no {@code size} param. */
    public static final String DEFAULT_PAGE_SIZE = "100";

    /** The clamp on any {@code size} a caller does send. */
    public static final int MAX_PAGE_SIZE = Integer.parseInt(DEFAULT_PAGE_SIZE);

    private PageSize() {
    }
}
