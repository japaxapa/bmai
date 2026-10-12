import { useCallback } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";

/**
 * Page position for a paged list, kept in the URL (frontend-architecture
 * §Data fetching: state that must survive a reload lives in search params,
 * like filters). One home for it across every list — see ADR 0019.
 *
 * `page` is 0-based: the same number the API's `?page=` takes, so the URL
 * param *is* the API param and nothing converts between them. Anything that
 * is not a non-negative integer (absent, "abc", "-2") reads as page 0.
 *
 * Other search params are preserved when the page changes — a future filter
 * (#6/#7/#8) shares the URL without this hook knowing about it.
 */
export function usePageParam() {
  const searchParams = useSearchParams();
  const pathname = usePathname();
  const router = useRouter();

  const raw = searchParams.get("page");
  const page = raw !== null && /^\d+$/.test(raw) ? Number(raw) : 0;

  /**
   * Move to a page. `replace`, not `push`: the URL stays shareable, but
   * clicking through pages must not litter the history stack (the back
   * button leaves the list, not the previous page). Page 0 drops the param
   * entirely — the default URL stays clean.
   */
  const goToPage = useCallback(
    (next: number) => {
      const target = Number.isFinite(next) ? Math.max(0, Math.trunc(next)) : 0;
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
