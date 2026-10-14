/**
 * The one definition of a valid page. The invariant: a page is a
 * non-negative integer; anything else reads as page 0. The two entry points
 * differ only in what their input's failure *means*. A string is evidence
 * from outside — a typed or carried URL — so it must be plainly integer
 * text ("abc", "-2", "1.7" are treated as "no page given", never guessed
 * at). A number is evidence from our own arithmetic (`envelope.page - 1`
 * off the front of a list), so intent is recovered instead: floored,
 * negatives clamped to 0, only truly broken values (NaN, Infinity) falling
 * back to 0.
 *
 * Pure by design, and in its own leaf module for #44 / ADR 0020: the
 * server-side seed reuses this same parser so seed and key always agree —
 * and it must, because the module `usePageParam` lives in imports
 * `next/navigation` client hooks, which may not enter the RSC graph.
 * `use-page-param` re-exports it, so callers keep importing it from there.
 */
export function toPage(value: string | number | null | undefined): number {
  if (typeof value === "string") return /^\d+$/.test(value) ? Number(value) : 0;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(0, Math.trunc(parsed)) : 0;
}
