"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { cache } from "react";

/**
 * The one factory for app query clients (ADR 0020).
 *
 * `staleTime: 30_000` is the no-double-fetch mechanism: a server seed is
 * fresh at hydration, so the island's first mount has nothing to refetch
 * (architecture.md §8). It absorbs hydration latency and server/client
 * clock skew; `Infinity` would rot lists, `0` was the forbidden extra
 * fetch. Writes are unaffected — `invalidateQueries` overrides staleTime.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        refetchOnWindowFocus: false,
        staleTime: 30_000,
      },
    },
  });
}

// One client per environment (TanStack's documented Next.js pattern):
// - server  → new per request (`cache()` shares it across one request's
//   renders; the useState below is the isolation), so user A's seeded rows
//   can never be served into user B's HTML — hydration only overwrites
//   entries that are newer, it never clears them.
// - browser → one module-level instance for the page's lifetime.
// - tests   → fresh per render, so one test's cache can't answer for the
//   next one.
const serverQueryClient = cache(createQueryClient);
let browserQueryClient: QueryClient | undefined;

export function getQueryClient(): QueryClient {
  if (typeof window === "undefined") {
    return serverQueryClient();
  }
  if (process.env.NODE_ENV === "test") {
    return createQueryClient();
  }
  return (browserQueryClient ??= createQueryClient());
}

export function QueryProvider({ children }: { children: ReactNode }) {
  // The initializer runs once per mount: per request on the server, the
  // singleton in the browser, a fresh client per render in tests.
  const [queryClient] = useState(getQueryClient);
  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
