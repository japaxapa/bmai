import { beforeEach, describe, expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { HydrationBoundary } from "@tanstack/react-query";
import { SESSION_COOKIE } from "@/lib/auth/session-cookie";
import { sessionInit } from "@/lib/api/server";
import { seedTable } from "@/lib/api/seed";
import { categoriesQuery } from "@/features/categories/api";
import { DEFAULT_API_BASE_URL } from "@/lib/api/base-url";
import { makeEnvelope } from "@/test/envelope";
import { server } from "@/test/server";
import { AppProviders } from "@/providers";
import { CategoriesPanel } from "@/app/(dashboard)/categories/_components/categories-panel";

// jsdom never mounts the app router: the island's usePageParam() would blow
// up without this double (same double categories.test.tsx installs).
vi.mock("next/navigation", () => import("@/test/next-navigation"));

/**
 * The server-seed seam (issue #44, ADR 0020): the request-scope boundary
 * (`cookies()` from `next/headers`) is the mock — jsdom has no request —
 * so everything above it (`sessionInit`, later `seedTable`) runs for real.
 * The real page's first HTML is Playwright's territory (testing-strategy §5).
 */

// The one framework boundary: a request-scoped cookie store. Everything
// above it (sessionInit, seedTable, the factory) runs for real.
const session = vi.hoisted(() => ({
  cookie: undefined as string | undefined,
  unavailable: false,
}));

vi.mock("next/headers", () => ({
  cookies: async () => {
    if (session.unavailable) {
      throw new Error("cookies() was called outside a request scope");
    }
    return {
      get: (name: string) =>
        name === SESSION_COOKIE && session.cookie !== undefined
          ? { name, value: session.cookie }
          : undefined,
    };
  },
}));

beforeEach(() => {
  session.cookie = undefined;
  session.unavailable = false;
  // The island's provider also signs in (auth is a query like any other).
  server.use(
    http.get(`${DEFAULT_API_BASE_URL}/api/auth/me`, () =>
      HttpResponse.json({ email: "admin@bms.local", role: "ADMIN" }),
    ),
  );
});

describe("server seed", () => {
  test("sessionInit forwards the session cookie verbatim, and is empty without one", async () => {
    expect(await sessionInit()).toEqual({});

    session.cookie = "eyJhbGciOiJIUzI1NiJ9.ab-c.d-e";
    expect(await sessionInit()).toEqual({
      headers: { Cookie: `${SESSION_COOKIE}=eyJhbGciOiJIUzI1NiJ9.ab-c.d-e` },
    });
  });

  test("fetches the page the URL asks for, under the island's own query key", async () => {
    const seen: { page: string | null; cookie: string | null }[] = [];
    server.use(
      http.get(`${DEFAULT_API_BASE_URL}/api/categories`, ({ request }) => {
        const url = new URL(request.url);
        seen.push({
          page: url.searchParams.get("page"),
          cookie: request.headers.get("Cookie"),
        });
        return HttpResponse.json(
          makeEnvelope([{ id: 1, name: "Grãos", active: true }]),
        );
      }),
    );
    session.cookie = "seed-token";

    const state = await seedTable(categoriesQuery, { page: "3" });

    // The seed's one request: the page from the URL, the session attached.
    expect(seen).toEqual([
      { page: "3", cookie: `${SESSION_COOKIE}=seed-token` },
    ]);
    // Key-addressed (ADR 0020): the dehydrated entry sits exactly where the
    // island will look for it — same factory, same page number.
    expect(state.queries).toHaveLength(1);
    expect(state.queries[0].queryKey).toEqual(categoriesQuery(3).queryKey);
    expect(state.queries[0].state.data).toEqual(
      makeEnvelope([{ id: 1, name: "Grãos", active: true }]),
    );
  });

  // Seed and island must agree on the page number for every URL a browser
  // can carry: the island parses with URLSearchParams `.get` (first value
  // wins) → toPage, so the seed rebuilds the same params and does the same
  // (ADR 0020's parse-parity rule — a drift here is a wasted seed at best).
  const parseCases: Array<
    [name: string, expected: number, searchParams: Record<string, string | string[] | undefined>]
  > = [
    ["no page param", 0, {}],
    ["?page=0", 0, { page: "0" }],
    ["?page=7", 7, { page: "7" }],
    ["garbage ?page=abc", 0, { page: "abc" }],
    ["repeated ?page=7&page=7", 7, { page: ["7", "7"] }],
  ];
  test.each(parseCases)("reads %s as page %i", async (_case, expected, searchParams) => {
    const requested: (string | null)[] = [];
    server.use(
      http.get(`${DEFAULT_API_BASE_URL}/api/categories`, ({ request }) => {
        requested.push(new URL(request.url).searchParams.get("page"));
        return HttpResponse.json(makeEnvelope([]));
      }),
    );

    const state = await seedTable(categoriesQuery, searchParams);

    expect(requested).toEqual([String(expected)]);
    expect(state.queries[0].queryKey).toEqual(
      categoriesQuery(expected).queryKey,
    );
  });

  // The degradation rule (ADR 0020): the seed may only improve the first
  // paint. Any failure — a refused backend, a request scope the cookie
  // store can't answer in — comes back as an empty state, so the island
  // fetches client-side exactly as it does today.
  test("degrades to an empty state instead of ever rejecting", async () => {
    server.use(
      http.get(`${DEFAULT_API_BASE_URL}/api/categories`, () =>
        HttpResponse.json({ detail: "boom" }, { status: 500 }),
      ),
    );

    const refused = await seedTable(categoriesQuery, {});
    expect(refused.queries).toEqual([]);

    session.unavailable = true;
    const noRequestScope = await seedTable(categoriesQuery, {});
    expect(noRequestScope.queries).toEqual([]);
  });

  // The point of the seed (issue #44 AC #2, testing-strategy §4): the
  // dehydrated state lands under the island's own key, so the first client
  // render already has the rows — no "Loading…" flash, no second fetch
  // within staleTime.
  test("hydrates the island: rows on first paint, no client refetch", async () => {
    let gets = 0;
    server.use(
      http.get(`${DEFAULT_API_BASE_URL}/api/categories`, ({ request }) => {
        gets += 1;
        const page = new URL(request.url).searchParams.get("page") ?? "0";
        return HttpResponse.json(
          makeEnvelope([{ id: 1, name: "Trigo", active: true }], {
            page: Number(page),
          }),
        );
      }),
    );
    session.cookie = "seed-token";

    const state = await seedTable(categoriesQuery, {});
    expect(gets).toBe(1); // exactly the seed's fetch

    render(
      <AppProviders>
        <HydrationBoundary state={state}>
          <CategoriesPanel />
        </HydrationBoundary>
      </AppProviders>,
    );

    // Synchronously, before any effect: the rows are there, no loading
    // indicator of any shape (the AC's "no client-side Loading… flash").
    expect(screen.getByText("Trigo")).toBeInTheDocument();
    expect(screen.queryByRole("status")).toBeNull();

    // Under staleTime 30_000 the seeded entry is fresh at mount, so the
    // island's refetch trigger must fire nothing: wait long enough to catch
    // a wrong one, then prove the count never moved (the no-double-fetch
    // guarantee, testing-strategy §4 / ADR 0020).
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(gets).toBe(1);
  });
});
