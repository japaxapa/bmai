package com.example.bms.shared;

import java.util.List;

/**
 * The window a paged read fetches: page and size <em>after</em> both guards,
 * so the values the SQL uses (OFFSET/LIMIT) and the values the envelope
 * reports are the same by construction.
 *
 * <p>The guards must run <em>before</em> the fetch — a negative page becomes
 * a negative {@code OFFSET} (a SQL error), {@code size 0} would make
 * {@link #envelope} divide by zero, and the ceiling is api-design's
 * "size ≤ 100". Handing the raw params to the fetch and clamping only for the
 * response would make the envelope describe a window nobody fetched, which is
 * why there is no way to get at the unclamped values: build the window first,
 * fetch with {@link #page()} / {@link #size()}.
 *
 * <p>One home for rules that used to be copy-pasted per service (see
 * {@link PageSize} for the constants both derive from).
 */
public record PageWindow(int page, int size) {

    /** Both guards, in one place. */
    public static PageWindow of(int page, int size) {
        return new PageWindow(
                Math.max(page, 0),
                Math.clamp(size, 1, PageSize.MAX_PAGE_SIZE));
    }

    /**
     * The envelope for this window: the page count is the ceiling of
     * {@code total / size}, in one place so every collection rounds the same
     * way ({@code total = 0} answers {@code totalPages = 0}).
     */
    public <T> PagedEnvelope<T> envelope(List<T> items, long total) {
        return new PagedEnvelope<>(items, page, size, total,
                (int) ((total + size - 1) / size));
    }
}
