import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, test, vi } from "vitest";
import { server } from "@/test/server";
import { makeEnvelope } from "@/test/envelope";
import { DEFAULT_API_BASE_URL } from "@/lib/api/base-url";
import { AppProviders } from "@/providers";
import { ProductsPanel } from "@/app/(dashboard)/products/_components/products-panel";
import { Toaster } from "@/components/ui/sonner";

// jsdom never mounts the app router, so anything calling useRouter() /
// useSearchParams() would blow up without this double.
vi.mock("next/navigation", () => import("@/test/next-navigation"));

const PRODUCTS_URL = `${DEFAULT_API_BASE_URL}/api/products`;
const CATEGORIES_URL = `${DEFAULT_API_BASE_URL}/api/categories`;
const ME_URL = `${DEFAULT_API_BASE_URL}/api/auth/me`;

type Role = "ADMIN" | "MANAGER" | "EMPLOYEE";
type Product = {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  price: number;
  cost: number;
  minimumStock: number;
  stock: number;
  categoryId: number;
  categoryName: string;
  active: boolean;
};

/** The row most scenarios start from — a live product in Padaria. */
const CAFE_ROW: Product = {
  id: 7,
  sku: "CAFE-1",
  name: "Café 500g",
  description: "Torra média",
  price: 24.9,
  cost: 15,
  minimumStock: 10,
  stock: 42,
  categoryId: 1,
  categoryName: "Padaria",
  active: true,
};

function signInAs(role: Role, email: string) {
  server.use(http.get(ME_URL, () => HttpResponse.json({ email, role })));
}

/** The vocabulary the form's category dropdown reads — any signed-in role may. */
function categoriesLoaded() {
  server.use(
    http.get(CATEGORIES_URL, () =>
      HttpResponse.json(
        makeEnvelope([
          { id: 1, name: "Padaria", active: true },
          { id: 2, name: "Bebidas", active: true },
        ]),
      ),
    ),
  );
}

// The page is an async server component (#44: it seeds the list during the
// HTML render), which jsdom cannot render — the panel island is the test seam
// (testing-strategy §4); the seed side (search-parse parity, hydration, no
// double fetch) is pinned in seed.test.tsx against this panel.
function renderPage() {
  return render(
    <AppProviders>
      <ProductsPanel />
    </AppProviders>,
  );
}

describe("products panel", () => {
  // Each render gets a fresh QueryClient (query-provider's test branch,
  // ADR 0020), so no test can be answered from another test's cache.
  test("lists the products the API returns, with sku, category and the deactivated flag", async () => {
    signInAs("ADMIN", "admin@bms.local");
    categoriesLoaded();
    server.use(
      http.get(PRODUCTS_URL, () =>
        HttpResponse.json(
          makeEnvelope([
            CAFE_ROW,
            {
              id: 8,
              sku: "TRIGO-2",
              name: "Trigo 1kg",
              description: null,
              price: 5.5,
              cost: 3.2,
              minimumStock: 0,
              stock: 120,
              categoryId: 1,
              categoryName: "Padaria",
              active: false,
            },
          ]),
        ),
      ),
    );

    renderPage();

    expect(await screen.findByText("Café 500g")).toBeInTheDocument();
    expect(screen.getByText("CAFE-1")).toBeInTheDocument();
    expect(screen.getByText("TRIGO-2")).toBeInTheDocument();
    // The category link travels as the resolved name, not a bare id. (The
    // dropdown carries the vocabulary too — scope to the rows.)
    expect(within(screen.getByRole("list")).getAllByText("Padaria")).toHaveLength(2);
    // GLOSSARY "Deactivated": the row stays in the list, marked — never gone.
    expect(screen.getByText("Deactivated")).toBeInTheDocument();
  });

  // The Toaster is deliberately NOT mounted in this file: the ticket's AC says
  // the duplicate SKU is "rendered inline in the UI, never a raw failure",
  // and a toast-only implementation must not be able to satisfy these tests.
  test("creating a product posts the whole body and shows it in the list", async () => {
    signInAs("ADMIN", "admin@bms.local");
    categoriesLoaded();
    let created = false;
    const requests: Request[] = [];
    server.use(
      http.get(PRODUCTS_URL, () =>
        HttpResponse.json(
          created
            ? makeEnvelope([
                {
                  id: 9,
                  sku: "NOVO-1",
                  name: "Novo produto",
                  description: null,
                  price: 9.9,
                  cost: 5,
                  minimumStock: 2,
                  stock: 0,
                  categoryId: 1,
                  categoryName: "Padaria",
                  active: true,
                },
                CAFE_ROW,
              ])
            : makeEnvelope([CAFE_ROW]),
        ),
      ),
      http.post(PRODUCTS_URL, async ({ request }) => {
        requests.push(request);
        created = true;
        return HttpResponse.json(
          {
            id: 9,
            sku: "NOVO-1",
            name: "Novo produto",
            description: null,
            price: 9.9,
            cost: 5,
            minimumStock: 2,
            stock: 0,
            categoryId: 1,
            categoryName: "Padaria",
            active: true,
          },
          { status: 201 },
        );
      }),
    );
    const user = userEvent.setup();

    renderPage();
    await screen.findByText("CAFE-1");

    await user.type(screen.getByLabelText("SKU"), "NOVO-1");
    await user.type(screen.getByLabelText("Name"), "Novo produto");
    await user.type(screen.getByLabelText("Price"), "9.90");
    await user.type(screen.getByLabelText("Cost"), "5");
    await user.type(screen.getByLabelText("Minimum stock"), "2");
    await user.selectOptions(screen.getByLabelText("Category"), "1");
    await user.click(screen.getByRole("button", { name: "Create product" }));

    // Only the refetch after the mutation can bring this row in, so a form
    // that forgets to invalidate the list fails here.
    expect(await screen.findByText("Novo produto")).toBeInTheDocument();
    expect(requests).toHaveLength(1);
    expect(requests[0].method).toBe("POST");
    expect(requests[0].credentials).toBe("include");
    // The whole shape, numbers as numbers, empty description as null — the
    // server's DTO is the contract, not the form's strings.
    expect(await requests[0].json()).toEqual({
      sku: "NOVO-1",
      name: "Novo produto",
      description: null,
      price: 9.9,
      cost: 5,
      minimumStock: 2,
      categoryId: 1,
    });
  });

  test("prints the conflict detail inline when the SKU is already taken", async () => {
    signInAs("ADMIN", "admin@bms.local");
    categoriesLoaded();
    server.use(
      http.get(PRODUCTS_URL, () => HttpResponse.json(makeEnvelope([CAFE_ROW]))),
      http.post(PRODUCTS_URL, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Conflict",
            status: 409,
            detail: "A product with SKU CAFE-1 already exists.",
          },
          {
            status: 409,
            headers: { "Content-Type": "application/problem+json" },
          },
        ),
      ),
    );
    const user = userEvent.setup();

    renderPage();
    await screen.findByText("CAFE-1");

    await user.type(screen.getByLabelText("SKU"), "cafe-1");
    await user.type(screen.getByLabelText("Name"), "Repetido");
    await user.type(screen.getByLabelText("Price"), "10");
    await user.selectOptions(screen.getByLabelText("Category"), "1");
    await user.click(screen.getByRole("button", { name: "Create product" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "A product with SKU CAFE-1 already exists.",
    );
    // The refusal stayed a refusal: no row ever took that SKU. (The typed
    // value in an input is not text content — an exact match can only be a row.)
    expect(screen.queryByText("Repetido")).toBeNull();
  });

  test("shows the API's field message on the field it names", async () => {
    signInAs("ADMIN", "admin@bms.local");
    categoriesLoaded();
    server.use(
      http.get(PRODUCTS_URL, () => HttpResponse.json(makeEnvelope([CAFE_ROW]))),
      http.post(PRODUCTS_URL, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Bad Request",
            status: 400,
            detail: "Validation failed",
            fieldErrors: {
              price: ["Price must be greater than zero."],
            },
          },
          {
            status: 400,
            headers: { "Content-Type": "application/problem+json" },
          },
        ),
      ),
    );
    const user = userEvent.setup();

    renderPage();
    await screen.findByText("CAFE-1");

    // Long enough to pass the form's own courtesy check and reach the API —
    // the server's message is the one that must come back.
    await user.type(screen.getByLabelText("SKU"), "ZERO-1");
    await user.type(screen.getByLabelText("Name"), "Grátis");
    await user.type(screen.getByLabelText("Price"), "0");
    await user.selectOptions(screen.getByLabelText("Category"), "1");
    await user.click(screen.getByRole("button", { name: "Create product" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Price must be greater than zero.",
    );
    expect(screen.getByLabelText("Price")).toHaveAttribute("aria-invalid", "true");
  });

  test("hides the write controls from a signed-in employee", async () => {
    signInAs("EMPLOYEE", "clerk@bms.local");
    categoriesLoaded();
    server.use(
      http.get(PRODUCTS_URL, () => HttpResponse.json(makeEnvelope([CAFE_ROW]))),
    );

    renderPage();

    // UI hides by role, UX only — the backend refuses independently
    // (frontend-architecture §State).
    expect(await screen.findByText("CAFE-1")).toBeInTheDocument();
    expect(screen.queryByLabelText("SKU")).toBeNull();
    expect(
      screen.queryByRole("button", { name: "Create product" }),
    ).toBeNull();
  });

  test("shows the write controls to a manager (business-rules §4)", async () => {
    signInAs("MANAGER", "supervisor@bms.local");
    categoriesLoaded();
    server.use(
      http.get(PRODUCTS_URL, () => HttpResponse.json(makeEnvelope([CAFE_ROW]))),
    );

    renderPage();

    expect(
      await screen.findByRole("button", { name: "Create product" }),
    ).toBeInTheDocument();
  });

  test("switches a product off from its row and says so", async () => {
    signInAs("ADMIN", "admin@bms.local");
    categoriesLoaded();
    let active = true;
    const requests: Request[] = [];
    server.use(
      http.get(PRODUCTS_URL, () =>
        HttpResponse.json(makeEnvelope([{ ...CAFE_ROW, active }])),
      ),
      http.put(`${PRODUCTS_URL}/7/status`, async ({ request }) => {
        requests.push(request);
        active = false;
        return HttpResponse.json({ ...CAFE_ROW, active: false });
      }),
    );
    const user = userEvent.setup();

    renderPage();
    await screen.findByText("CAFE-1");

    await user.click(
      screen.getByRole("button", { name: "Deactivate Café 500g" }),
    );

    expect(
      await screen.findByRole("button", { name: "Reactivate Café 500g" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Deactivated")).toBeInTheDocument();
    expect(requests).toHaveLength(1);
    expect(requests[0].method).toBe("PUT");
    expect(requests[0].credentials).toBe("include");
    expect(await requests[0].json()).toEqual({ active: false });
  });

  // A row action has no form to print into: its refusal goes to the toast.
  // The Toaster is mounted here (unlike the default renderPage above) because
  // the assertion is exactly that the toast — not the create form — carries
  // the sentence.
  test("a refused status change surfaces as a toast, never inside the form", async () => {
    signInAs("ADMIN", "admin@bms.local");
    categoriesLoaded();
    server.use(
      http.get(PRODUCTS_URL, () => HttpResponse.json(makeEnvelope([CAFE_ROW]))),
      http.put(`${PRODUCTS_URL}/7/status`, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Not Found",
            status: 404,
            detail: "No product with id 7",
          },
          {
            status: 404,
            headers: { "Content-Type": "application/problem+json" },
          },
        ),
      ),
    );
    const user = userEvent.setup();

    render(
      <AppProviders>
        <Toaster />
        <ProductsPanel />
      </AppProviders>,
    );
    await screen.findByText("CAFE-1");

    await user.click(
      screen.getByRole("button", { name: "Deactivate Café 500g" }),
    );

    // The sentence reached the user...
    expect(await screen.findByText("No product with id 7")).toBeInTheDocument();
    // ...and the create form's alert stayed empty: a status failure must not
    // masquerade as a SKU conflict.
    expect(screen.queryByRole("alert")).toBeNull();
  });

  test("edits a product through the same form, seeded from the row", async () => {
    signInAs("ADMIN", "admin@bms.local");
    categoriesLoaded();
    let current = CAFE_ROW;
    const requests: Request[] = [];
    server.use(
      http.get(PRODUCTS_URL, () => HttpResponse.json(makeEnvelope([current]))),
      http.put(`${PRODUCTS_URL}/7`, async ({ request }) => {
        // An undici Request body is single-read: keep an unread copy for the
        // assertions below and consume the original to track the edit.
        requests.push(request.clone());
        const body = (await request.json()) as Partial<Product>;
        current = { ...current, ...body };
        return HttpResponse.json(current);
      }),
    );
    const user = userEvent.setup();

    renderPage();
    await screen.findByText("CAFE-1");

    await user.click(screen.getByRole("button", { name: "Edit Café 500g" }));

    // initialData: every box holds the row's own value the moment the form
    // opens — no second fetch to fill it in (testing-strategy §4).
    expect(screen.getByDisplayValue("CAFE-1")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Café 500g")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Torra média")).toBeInTheDocument();
    expect(screen.getByDisplayValue("24.9")).toBeInTheDocument();
    expect(screen.getByDisplayValue("15")).toBeInTheDocument();
    expect(screen.getByDisplayValue("10")).toBeInTheDocument();

    const nameField = screen.getByDisplayValue("Café 500g");
    await user.clear(nameField);
    await user.type(nameField, "Café 500g torra escura");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText("Café 500g torra escura")).toBeInTheDocument();
    expect(requests).toHaveLength(1);
    expect(requests[0].method).toBe("PUT");
    // The whole shape again — an edit cannot silently blank a field left out.
    expect(await requests[0].json()).toEqual({
      sku: "CAFE-1",
      name: "Café 500g torra escura",
      description: "Torra média",
      price: 24.9,
      cost: 15,
      minimumStock: 10,
      categoryId: 1,
    });
  });

  // filter state in the URL (testing-strategy §4, ADR 0019): a search is
  // state that must survive a reload and stay shareable.
  test("searches the API as the term is typed, keeps it in the URL and re-roots the page", async () => {
    signInAs("ADMIN", "admin@bms.local");
    categoriesLoaded();
    window.history.replaceState({}, "", "/products?page=1");
    const requested: string[] = [];
    server.use(
      http.get(PRODUCTS_URL, ({ request }) => {
        requested.push(new URL(request.url).search);
        return HttpResponse.json(
          new URL(request.url).searchParams.get("search") === "cafe"
            ? makeEnvelope([CAFE_ROW])
            : makeEnvelope([]),
        );
      }),
    );
    const user = userEvent.setup();

    renderPage();
    await screen.findByRole("textbox", { name: "Search" });

    await user.type(screen.getByRole("textbox", { name: "Search" }), "cafe");

    // Only the refetch carrying the term can bring the matching row in.
    expect(await screen.findByText("CAFE-1")).toBeInTheDocument();
    // The request carried the term, on page 0 — the old offset belongs to
    // the old result set...
    expect(requested.at(-1)).toContain("search=cafe");
    expect(requested.at(-1)).toContain("page=0");
    // ...and the URL keeps the term (shareable), without the stale page.
    expect(window.location.search).toBe("?search=cafe");
  });

  test("keeps the search while paging", async () => {
    signInAs("ADMIN", "admin@bms.local");
    categoriesLoaded();
    window.history.replaceState({}, "", "/products?search=cafe");
    const requested: string[] = [];
    server.use(
      http.get(PRODUCTS_URL, ({ request }) => {
        const url = new URL(request.url);
        requested.push(url.search);
        const page = url.searchParams.get("page") ?? "0";
        return HttpResponse.json(
          page === "0"
            ? {
                items: [CAFE_ROW],
                page: 0,
                size: 2,
                total: 3,
                totalPages: 2,
              }
            : {
                items: [{ ...CAFE_ROW, id: 99, sku: "CAFE-2", name: "Café 250g" }],
                page: 1,
                size: 2,
                total: 3,
                totalPages: 2,
              },
        );
      }),
    );
    const user = userEvent.setup();

    renderPage();
    await screen.findByText("CAFE-1");
    // The term from the URL is what the box shows — the state round-trips.
    expect(screen.getByRole("textbox", { name: "Search" })).toHaveValue("cafe");

    await user.click(screen.getByRole("button", { name: "Next" }));

    expect(await screen.findByText("CAFE-2")).toBeInTheDocument();
    expect(requested.at(-1)).toContain("page=1");
    expect(requested.at(-1)).toContain("search=cafe");
    expect(window.location.search).toBe("?search=cafe&page=1");
  });
});
