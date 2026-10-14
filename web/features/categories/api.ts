import { queryOptions } from "@tanstack/react-query";
import { apiBaseUrl } from "@/lib/api/base-url";
import type { PagedEnvelope } from "@/types/paged-envelope";

/**
 * The categories list's fetch and query key — the `features/<f>/api.ts` home
 * frontend-architecture §Structure mandates (the panel used to hold these;
 * #44 moved them out because the server seed calls them too, and a value
 * export can't be imported from a `"use client"` module).
 */

/** One row of the vocabulary (GLOSSARY Category). */
export type Category = { id: number; name: string; active: boolean };

export const CATEGORIES_KEY = ["categories"] as const;

/**
 * One page of categories. The page goes on the wire (`?page=`, 0-based —
 * the same number the URL carries, ADR 0019), so every row is reachable;
 * the API's default first page can only ever hold 100 (api-design size
 * ≤ 100).
 *
 * `init` exists so the server-side seed (ADR 0020) can inject the session
 * cookie header; client callers never pass it.
 */
export async function fetchCategories(
  page: number,
  init?: RequestInit,
): Promise<PagedEnvelope<Category>> {
  const response = await fetch(
    `${apiBaseUrl()}/api/categories?page=${page}`,
    {
      ...init,
      credentials: "include",
    },
  );
  if (!response.ok) {
    throw new Error(`GET /api/categories -> ${response.status}`);
  }
  return (await response.json()) as PagedEnvelope<Category>;
}

/**
 * The one query definition for a page of categories: the key carries the
 * page (`["categories", N]`, ADR 0019), so seed and island agree by
 * construction — both go through this factory (ADR 0020).
 */
export function categoriesQuery(page: number, init?: RequestInit) {
  return queryOptions({
    queryKey: [...CATEGORIES_KEY, page],
    queryFn: () => fetchCategories(page, init),
  });
}
