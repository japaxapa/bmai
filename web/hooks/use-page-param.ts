import { useCallback } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { toPage } from "@/hooks/to-page";

// The pure rule lives in its own leaf module (the server seed imports it
// too, #44 / ADR 0020 — this module's `next/navigation` imports can't
// enter the RSC graph); the interface stays here, unchanged.
export { toPage };

/**
 * Page position for a paged list, kept in the URL (frontend-architecture
 * §Data fetching: state that must survive a reload lives in search params,
 * like filters). One home for it across every list — see ADR 0019.
 *
 * `page` is 0-based: the same number the API's `?page=` takes, so the URL
 * param *is* the API param and nothing converts between them. What counts as
 * a valid page lives in one place, `toPage` (its own leaf module, re-exported
 * above).
 *
 * Other search params are preserved when the page changes — a future filter
 * (#6/#7/#8) shares the URL without this hook knowing about it.
 */

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
