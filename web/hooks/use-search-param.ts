import { useCallback } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";

/**
 * One filter term in the URL — the same rule {@link usePageParam} applies
 * to the page number (ADR 0019): state that must survive a reload lives in
 * search params. Generic on purpose (frontend-architecture §Structure: the
 * `hooks/` home for app-wide hooks), because #7/#8 (suppliers, customers)
 * get filters that look just like the products search.
 *
 * The caller names which other params the new term invalidates: a search
 * re-roots the list, so the products panel clears `page` — the old offset
 * belongs to the old result set, not the new one.
 */
export function useSearchParam(name: string, clears: readonly string[] = []) {
  const searchParams = useSearchParams();
  const pathname = usePathname();
  const router = useRouter();

  const value = searchParams.get(name) ?? "";

  const setValue = useCallback(
    (next: string) => {
      const params = new URLSearchParams(searchParams.toString());
      if (next) {
        params.set(name, next);
      } else {
        params.delete(name);
      }
      for (const cleared of clears) {
        params.delete(cleared);
      }
      const query = params.toString();
      // `replace`, not `push`: typing a term must not litter the history
      // stack (the back button leaves the list, not the previous spelling).
      router.replace(query ? `${pathname}?${query}` : pathname, {
        scroll: false,
      });
    },
    [clears, name, pathname, router, searchParams],
  );

  return [value, setValue] as const;
}
