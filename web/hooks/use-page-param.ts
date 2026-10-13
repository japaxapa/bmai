import { useCallback } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";

/**
 * Page position for a paged list, kept in the URL (frontend-architecture
 * §Data fetching: state that must survive a reload lives in search params,
 * like filters). One home for it across every list — see ADR 0019.
 *
 * `page` is 0-based: the same number the API's `?page=` takes, so the URL
 * param *is* the API param and nothing converts between them. What counts as
 * a valid page lives in one place, `toPage` below.
 *
 * Other search params are preserved when the page changes — a future filter
 * (#6/#7/#8) shares the URL without this hook knowing about it.
 */

/**
 * The one definition of a valid page. The invariant: a page is a
 * non-negative integer; anything else reads as page 0. The two entry points
 * differ only in what their input's failure *means*. A string is evidence
 * from outside — a typed or carried URL — so it must be plainly integer
 * text ("abc", "-2", "1.7" are treated as "no page given", never guessed
 * at). A number is evidence from our own arithmetic (`envelope.page - 1`
 * off the front of a list), so intent is recovered instead: floored,
 * negatives clamped to 0, only truly broken values (NaN, Infinity) falling
 * back to 0. Pure by design — the server side of a seeded table (#44)
 * reuses this same parser so seed and key always agree.
 */
export function toPage(value: string | number | null | undefined): number {
  if (typeof value === "string") return /^\d+$/.test(value) ? Number(value) : 0;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(0, Math.trunc(parsed)) : 0;
}

export function usePageParam() {
  const searchParams = useSearchParams();
  const pathname = usePathname();
  const router = useRouter();

  const page = toPage(searchParams.get("page"));

  /**
   * Move to a page. `replace`, not `push`: the URL stays shareable, but
   * clicking through pages must not litter the history stack (the back
   * button leaves the list, not the previous page). Page 0 drops the param
   * entirely — the default URL stays clean.
   */
  const goToPage = useCallback(
    (next: number) => {
      const target = toPage(next);
      if (target === page) return;
      const params = new URLSearchParams(searchParams.toString());
      if (target === 0) {
        params.delete("page");
      } else {
        params.set("page", String(target));
      }
      const query = params.toString();
      router.replace(query ? `${pathname}?${query}` : pathname, {
        scroll: false,
      });
    },
    [page, pathname, router, searchParams],
  );

  return { page, goToPage };
}
