import type { PagedEnvelope } from "@/types/paged-envelope";

/**
 * Build the paged envelope api-design.md:7 promises, with the defaults a
 * single-page list wants; a test overrides whatever its scenario needs
 * (`makeEnvelope(rows, { page: 2, total: 47, totalPages: 3 })`).
 *
 * The one place fixtures construct the envelope, so a contract change breaks
 * here at compile time instead of drifting through two test files.
 */
export function makeEnvelope<T>(
  items: T[],
  overrides: Partial<PagedEnvelope<T>> = {},
): PagedEnvelope<T> {
  return {
    items,
    page: 0,
    size: 100,
    total: items.length,
    totalPages: 1,
    ...overrides,
  };
}
