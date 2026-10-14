/**
 * The one "the rows aren't here yet" visual (ADR 0020): the Suspense
 * fallback while the server seeds a table island, and — same shape — the
 * client loading state `<PagedList>` shows when the seed missed and the
 * island fetches for real. One skeleton, both paths, so the degraded page
 * never looks like a different product than the seeded one.
 *
 * `role="status"` + the accessible name make it perceivable to assistive
 * tech and the thing tests look for instead of layout pixels.
 */
export function TableSkeleton() {
  return (
    <div role="status" aria-label="Loading…" className="flex flex-col gap-2">
      {[0, 1, 2, 3].map((row) => (
        <div key={row} className="flex items-center gap-3" aria-hidden="true">
          <div className="h-4 w-44 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800" />
          <div className="h-4 w-24 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800" />
        </div>
      ))}
    </div>
  );
}
