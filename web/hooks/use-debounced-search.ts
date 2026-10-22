import { useEffect, useState } from "react";
import { useSearchParam } from "@/hooks/use-search-param";

/**
 * A paged list's search term in two clocks — one home for the rule across
 * every list, so the timing and the reset cannot drift panel-to-panel
 * (the same reason {@link usePageParam} and `refusalHandlers` exist).
 *
 * - `search` is the URL param: state that must survive a reload lives in
 *   search params (ADR 0019), and the query key carries (page, search) —
 *   each pair its own cache entry, a prefix invalidation clearing all of
 *   them. A new term re-roots the list, so it clears `page`: the old
 *   offset belongs to the old result set, not the new one.
 * - `term` is the box's own value, so typing feels instant; it reaches
 *   the URL (and therefore the API) after a short pause — one request per
 *   thought, not one per keystroke. The `term === search` guard is what
 *   stops an URL-arrived term from re-writing itself.
 *
 * The panel wires the box (`value={term}`, `onChange` → `setTerm`) and
 * reads `search` into its query key; `usePageParam` stays the panel's own
 * call — paging and searching are separate concerns that share the URL.
 */
export function useDebouncedSearch() {
  const [search, setSearch] = useSearchParam("search", ["page"]);
  const [term, setTerm] = useState(search);
  useEffect(() => {
    if (term === search) return;
    const timer = setTimeout(() => setSearch(term), 300);
    return () => clearTimeout(timer);
  }, [term, search, setSearch]);
  return { term, setTerm, search };
}
