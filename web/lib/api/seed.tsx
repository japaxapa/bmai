import type { ReactNode } from "react";
import {
  dehydrate,
  HydrationBoundary,
  QueryClient,
  type DehydratedState,
  type QueryFunction,
  type QueryKey,
} from "@tanstack/react-query";
import { toPage } from "@/hooks/to-page";
import { sessionInit } from "@/lib/api/server";

/**
 * What a table island queries with: the key the island will read, and the
 * function that fills it (the shape `queryOptions(...)` returns). `init`
 * carries the server-only cookie header (ADR 0020); the island never
 * passes it.
 *
 * Generic in the key because each list's key carries its own tuple type —
 * re-widening it here would fight TanStack's variance rules for nothing.
 */
export type TableQueryFactory<TQueryKey extends QueryKey = QueryKey> = (
  page: number,
  init?: RequestInit,
) => {
  queryKey: TQueryKey;
  queryFn?: QueryFunction<unknown, TQueryKey> | undefined;
};

/**
 * Seed one paged list for its client island (ADR 0020): fetch the page the
 * URL asks for on the server, and hand the result back as dehydrated state
 * for a `<HydrationBoundary>`. The page number goes through the same
 * `toPage` and the key through the same factory the island queries with, so
 * a seed can only ever land on (or miss) the island's own entry — never on
 * a wrong one.
 *
 * `seedTable` (this function) and `TableSeed` (the RSC wrapper below) are a
 * deliberate near-homograph pair: same seam, two shapes — this one is the
 * plain, jsdom-callable core the seed suite tests; the component is what a
 * page mounts.
 */
export async function seedTable<TQueryKey extends QueryKey>(
  query: TableQueryFactory<TQueryKey>,
  searchParams: Record<string, string | string[] | undefined>,
): Promise<DehydratedState> {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  // Degradation rule (ADR 0020): never reject. prefetchQuery already
  // swallows fetch failures (they dehydrate to nothing); this guard also
  // covers everything around the fetch — the cookie store above all.
  try {
    const init = await sessionInit();
    const { queryKey, queryFn } = query(pageFromSearchParams(searchParams), init);
    // A factory without a queryFn throws inside this try → degrades like
    // any other failure, which is also the only sane answer to one.
    await client.prefetchQuery({ queryKey, queryFn });
  } catch {
    // An empty state is the island's status-quo path (ADR 0020).
  }
  return dehydrate(client);
}

/**
 * The page the URL asks for — parse parity with the island (ADR 0020):
 * rebuild the URLSearchParams the island reads (every repeated value
 * appended, first one wins from `.get`) and run the same `toPage` over
 * `.get("page")` — a string[] from a repeated `?page=` must never reach
 * `toPage` directly.
 */
function pageFromSearchParams(
  searchParams: Record<string, string | string[] | undefined>,
) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(searchParams)) {
    if (Array.isArray(value)) {
      for (const entry of value) params.append(key, entry);
    } else if (value !== undefined) {
      params.append(key, value);
    }
  }
  return toPage(params.get("page"));
}

type TableSeedProps<TQueryKey extends QueryKey> = {
  /** The island's own query factory — key and fetch, one home (ADR 0020). */
  query: TableQueryFactory<TQueryKey>;
  /** The page's `searchParams` prop, still a promise: awaited here, inside
   *  the caller's `<Suspense>`, so the route keeps its static shell. */
  searchParams: Promise<Record<string, string | string[] | undefined>>;
  /** The island itself — rendered only after the seed lands. */
  children: ReactNode;
};

/**
 * The server side of a seeded table (`TableSeed`, see `seedTable` above
 * for the core): await the URL, seed it, and wrap the island in the
 * `<HydrationBoundary>` that carries the result across the wire.
 *
 * Never rejects (seedTable's degradation rule): a failed seed arrives as
 * empty state, the boundary hydrates nothing, and the island behaves as if
 * unseeded (ADR 0020).
 */
export async function TableSeed<TQueryKey extends QueryKey>({
  query,
  searchParams,
  children,
}: TableSeedProps<TQueryKey>) {
  const state = await seedTable(query, await searchParams);
  return <HydrationBoundary state={state}>{children}</HydrationBoundary>;
}
