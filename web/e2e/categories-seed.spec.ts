import { expect, test } from "@playwright/test";
import { api, login, uniqueName } from "./support/fixtures";
import type { Category } from "../features/categories/api";
import type { PagedEnvelope } from "../types/paged-envelope";

/**
 * The #44 seed acceptance criteria, proven against the real route (issue
 * #45): jsdom can't render the async server component, so `seed.test.tsx`
 * covers the seam and these specs cover the page (ADR 0020, testing-strategy
 * §5). Two claims per page:
 *
 * 1. The first HTML the server sends already carries the rows — asserted on
 *    the raw document bytes (a plain GET, no JS), the strongest form of
 *    "first paint shows rows".
 * 2. The browser never fetches the list itself — zero client-side
 *    `/api/categories` requests — so no client-side "Loading…" state can
 *    exist (staleTime 30s keeps the hydrated entry fresh at mount).
 */

/** Every `/api/categories` request the *browser* makes, for the no-fetch proof. */
function trackListFetches(page: import("@playwright/test").Page): string[] {
  const fetched: string[] = [];
  page.on("request", (request) => {
    if (new URL(request.url()).pathname === "/api/categories") {
      fetched.push(request.url());
    }
  });
  return fetched;
}

async function envelope(
  request: import("@playwright/test").APIRequestContext,
  query: string,
): Promise<PagedEnvelope<Category>> {
  const response = await api(request, "get", `/api/categories${query}`);
  return (await response.json()) as PagedEnvelope<Category>;
}

test.describe("server seed (issue #44, ADR 0020)", () => {
  test("page 0: rows are in the first HTML, and the browser never fetches the list", async ({
    context,
    page,
  }) => {
    await login(context.request);
    const name = uniqueName("Trigo");
    await api(context.request, "post", "/api/categories", { name });

    // The first paint's actual bytes: a document GET executes no client JS,
    // so any row in here was put there by the server seed.
    const document = await context.request.get("/categories");
    expect(await document.text()).toContain(name);

    // The rendered page agrees, without a client round-trip to fill the list.
    // Scoped to the *visible* row: the PPR resume slot ships the rows in a
    // <div hidden id="S:..."> and swaps them into place (~200ms), so for a
    // moment two spans carry the name — a bare getByText(name) throws a
    // strict-mode violation in that window. Role queries skip display:none
    // subtrees, so exactly one listitem resolves in every phase.
    const listFetches = trackListFetches(page);
    await page.goto("/categories");
    await expect(page.getByRole("listitem").filter({ hasText: name })).toBeVisible();
    // No loading state of any shape once the page has loaded — the island's
    // query was hydrated fresh, so it never fell back to the skeleton.
    await expect(page.getByRole("status")).toHaveCount(0);
    expect(listFetches).toEqual([]);
  });

  test("?page=N deep link: the first HTML carries page N's rows", async ({
    context,
    page,
  }) => {
    await login(context.request);

    // The API pages at 100 (api-design size ≤ 100; the list fetches no
    // explicit size), so 101 rows make page 1 exist. The e2e database is
    // dropped and recreated per run (start-backend.mjs), and uniqueness
    // ignores case, so fresh names per run always fit.
    for (let i = 0; i < 101; i += 1) {
      await api(context.request, "post", "/api/categories", {
        name: uniqueName("Seed"),
      });
    }

    // API truth, read fresh: which rows page 1 owns right now, and a row that
    // page 1 must not contain. (Derived, not hardcoded — the first test's
    // row and any future seeds shift the boundaries.)
    const page0 = await envelope(context.request, "?page=0");
    const page1 = await envelope(context.request, "?page=1");
    const onPage1 = page1.items[0].name;
    const notOnPage1 = page0.items[0].name;

    // The deep-linked document already carries page 1's row — and not
    // page 0's: the seed read `?page=1` off the URL instead of defaulting.
    const html = await (await context.request.get("/categories?page=1")).text();
    expect(html).toContain(onPage1);
    expect(html).not.toContain(notOnPage1);

    // The rendered page agrees, again without a client fetch. Visible-scoped
    // for the same PPR resume-slot reason as the first test.
    const listFetches = trackListFetches(page);
    await page.goto("/categories?page=1");
    await expect(
      page.getByRole("listitem").filter({ hasText: onPage1 }),
    ).toBeVisible();
    await expect(page.getByRole("status")).toHaveCount(0);
    expect(listFetches).toEqual([]);
  });
});
