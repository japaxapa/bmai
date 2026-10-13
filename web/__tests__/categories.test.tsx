import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, test, vi } from "vitest";
import { server } from "@/test/server";
import { makeEnvelope } from "@/test/envelope";
import { DEFAULT_API_BASE_URL } from "@/lib/api/base-url";
import { AppProviders } from "@/providers";
import CategoriesPage from "@/app/(dashboard)/categories/page";
import { Toaster } from "@/components/ui/sonner";

// jsdom never mounts the app router, so anything calling useRouter() /
// useSearchParams() would blow up without this double.
vi.mock("next/navigation", () => import("@/test/next-navigation"));

const CATEGORIES_URL = `${DEFAULT_API_BASE_URL}/api/categories`;
const ME_URL = `${DEFAULT_API_BASE_URL}/api/auth/me`;

type Role = "ADMIN" | "MANAGER" | "EMPLOYEE";
type Category = { id: number; name: string; active: boolean };

function signInAs(role: Role, email: string) {
  server.use(http.get(ME_URL, () => HttpResponse.json({ email, role })));
}

function renderPage() {
  return render(
    <AppProviders>
      <CategoriesPage />
    </AppProviders>,
  );
}

describe("categories page", () => {
  // Every test names its own rows: the query client is shared for the file,
  // so a name used twice could be answered from the previous test's cache
  // instead of this test's handler.
  test("lists the categories the API returns, flagging the switched-off ones", async () => {
    signInAs("ADMIN", "admin@bms.local");
    server.use(
      http.get(CATEGORIES_URL, () =>
        HttpResponse.json(
          makeEnvelope([
            { id: 1, name: "Bebidas", active: true },
            { id: 2, name: "Limpeza", active: false },
          ]),
        ),
      ),
    );

    renderPage();

    expect(await screen.findByText("Bebidas")).toBeInTheDocument();
    expect(screen.getByText("Limpeza")).toBeInTheDocument();
    // GLOSSARY "Deactivated": the row stays in the list, marked — never gone.
    expect(screen.getByText("Deactivated")).toBeInTheDocument();
  });

  // The whole point of ticket #42: past the API's first page nothing may
  // silently vanish — the page travels to the API, and the pager reaches
  // the rows beyond it.
  test("reaches rows past the first page by paging, keeping the page in the URL", async () => {
    signInAs("ADMIN", "admin@bms.local");
    const requestedPages: string[] = [];
    server.use(
      http.get(CATEGORIES_URL, ({ request }) => {
        const page = new URL(request.url).searchParams.get("page") ?? "0";
        requestedPages.push(page);
        return HttpResponse.json(
          page === "0"
            ? {
                items: [{ id: 1, name: "Café", active: true }],
                page: 0,
                size: 2,
                total: 3,
                totalPages: 2,
              }
            : {
                items: [{ id: 3, name: "Trigo", active: true }],
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
    await screen.findByText("Café");
    expect(screen.getByText("1–1 of 3")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Next" }));

    expect(await screen.findByText("Trigo")).toBeInTheDocument();
    expect(screen.queryByText("Café")).toBeNull();
    // The request carried the page, and the URL keeps it (shareable).
    expect(requestedPages).toEqual(["0", "1"]);
    expect(window.location.search).toBe("?page=1");
  });

  test("creating a category posts it and shows it in the list", async () => {
    signInAs("ADMIN", "admin@bms.local");
    let created = false;
    const requests: Request[] = [];
    server.use(
      http.get(CATEGORIES_URL, () =>
        HttpResponse.json(
          created
            ? makeEnvelope([
                { id: 1, name: "Grãos", active: true },
                { id: 2, name: "Higiene", active: true },
              ])
            : makeEnvelope([{ id: 1, name: "Grãos", active: true }]),
        ),
      ),
      http.post(CATEGORIES_URL, async ({ request }) => {
        requests.push(request);
        created = true;
        return HttpResponse.json(
          { id: 2, name: "Higiene", active: true },
          { status: 201 },
        );
      }),
    );
    const user = userEvent.setup();

    renderPage();
    await screen.findByText("Grãos");

    await user.type(screen.getByRole("textbox", { name: "Name" }), "Higiene");
    await user.click(screen.getByRole("button", { name: "Create category" }));

    // Only the refetch after the mutation can bring this row in, so a form
    // that forgets to invalidate the list fails here.
    expect(await screen.findByText("Higiene")).toBeInTheDocument();
    expect(requests).toHaveLength(1);
    expect(requests[0].method).toBe("POST");
    expect(requests[0].credentials).toBe("include");
    expect(await requests[0].json()).toEqual({ name: "Higiene" });
  });

  // The Toaster is deliberately NOT mounted in this file: the ticket's AC says
  // the duplicate name is "rendered inline in the UI, never a raw failure",
  // and a toast-only implementation must not be able to satisfy this test.
  test("prints the conflict detail inline when the name is already taken", async () => {
    signInAs("ADMIN", "admin@bms.local");
    server.use(
      http.get(CATEGORIES_URL, () =>
        HttpResponse.json(makeEnvelope([{ id: 1, name: "Padaria", active: true }])),
      ),
      http.post(CATEGORIES_URL, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Conflict",
            status: 409,
            detail: "A category named Fraldas already exists.",
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
    await screen.findByText("Padaria");

    await user.type(screen.getByRole("textbox", { name: "Name" }), "Fraldas");
    await user.click(screen.getByRole("button", { name: "Create category" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "A category named Fraldas already exists.",
    );
    // The refusal stayed a refusal: no row ever took that name. (The typed
    // value in the input is not text content, and the alert's sentence is
    // longer than the name — an exact match here can only be a row.)
    expect(screen.queryByText("Fraldas")).toBeNull();
  });

  test("shows the API's field message on the field it names", async () => {
    signInAs("ADMIN", "admin@bms.local");
    server.use(
      http.get(CATEGORIES_URL, () =>
        HttpResponse.json(makeEnvelope([{ id: 1, name: "Frios", active: true }])),
      ),
      http.post(CATEGORIES_URL, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Bad Request",
            status: 400,
            detail: "Validation failed",
            fieldErrors: {
              name: ["Name must be at most 120 characters."],
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
    await screen.findByText("Frios");

    // Long enough to pass the form's own courtesy check and reach the API —
    // the server's message is the one that must come back.
    await user.type(screen.getByRole("textbox", { name: "Name" }), "x".repeat(121));
    await user.click(screen.getByRole("button", { name: "Create category" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Name must be at most 120 characters.",
    );
    expect(screen.getByRole("textbox", { name: "Name" })).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });

  test("hides the write controls from a signed-in non-admin", async () => {
    signInAs("EMPLOYEE", "clerk@bms.local");
    server.use(
      http.get(CATEGORIES_URL, () =>
        HttpResponse.json(makeEnvelope([{ id: 1, name: "Bebidas", active: true }])),
      ),
    );

    renderPage();

    // UI hides by role, UX only — the backend refuses independently
    // (frontend-architecture §State).
    expect(await screen.findByText("Bebidas")).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Name" })).toBeNull();
    expect(
      screen.queryByRole("button", { name: "Create category" }),
    ).toBeNull();
  });

  test("switches a category off from its row and says so", async () => {
    signInAs("ADMIN", "admin@bms.local");
    let active = true;
    const requests: Request[] = [];
    server.use(
      http.get(CATEGORIES_URL, () =>
        HttpResponse.json(
          makeEnvelope([{ id: 1, name: "Descartáveis", active }]),
        ),
      ),
      http.put(`${CATEGORIES_URL}/1/status`, async ({ request }) => {
        requests.push(request);
        active = false;
        return HttpResponse.json({
          id: 1,
          name: "Descartáveis",
          active: false,
        });
      }),
    );
    const user = userEvent.setup();

    renderPage();
    await screen.findByText("Descartáveis");

    await user.click(
      screen.getByRole("button", { name: "Deactivate Descartáveis" }),
    );

    expect(
      await screen.findByRole("button", { name: "Reactivate Descartáveis" }),
    ).toBeInTheDocument();
    expect(requests).toHaveLength(1);
    expect(requests[0].method).toBe("PUT");
    expect(requests[0].credentials).toBe("include");
    expect(await requests[0].json()).toEqual({ active: false });
  });

  // A row action has no form to print into: its refusal goes to the toast.
  // The Toaster is mounted here (unlike the default renderPage above) because
  // the assertion is exactly that the toast — not the create/rename form —
  // carries the sentence.
  test("a refused status change surfaces as a toast, never inside the form", async () => {
    signInAs("ADMIN", "admin@bms.local");
    server.use(
      http.get(CATEGORIES_URL, () =>
        HttpResponse.json(makeEnvelope([{ id: 7, name: "Sucos", active: true }])),
      ),
      http.put(`${CATEGORIES_URL}/7/status`, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Not Found",
            status: 404,
            detail: "Category 7 was not found.",
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
        <CategoriesPage />
      </AppProviders>,
    );
    await screen.findByText("Sucos");

    await user.click(
      screen.getByRole("button", { name: "Deactivate Sucos" }),
    );

    // The sentence reached the user...
    expect(await screen.findByText("Category 7 was not found.")).toBeInTheDocument();
    // ...and the create/rename form's alert stayed empty: a status failure
    // must not masquerade as a name conflict.
    expect(screen.queryByRole("alert")).toBeNull();
  });

  test("renames a category through the same form, seeded from the row", async () => {
    signInAs("ADMIN", "admin@bms.local");
    let current = "Perfumes";
    const requests: Request[] = [];
    server.use(
      http.get(CATEGORIES_URL, () =>
        HttpResponse.json(
          makeEnvelope([{ id: 1, name: current, active: true }]),
        ),
      ),
      http.put(`${CATEGORIES_URL}/1`, async ({ request }) => {
        // An undici Request body is single-read: keep an unread copy for the
        // assertions below and consume the original to track the rename.
        requests.push(request.clone());
        current = ((await request.json()) as { name: string }).name;
        return HttpResponse.json({ id: 1, name: current, active: true });
      }),
    );
    const user = userEvent.setup();

    renderPage();
    await screen.findByText("Perfumes");

    await user.click(screen.getByRole("button", { name: "Edit Perfumes" }));

    // initialData: the row's own value is in the box as soon as the form
    // opens — no second fetch to fill it in (testing-strategy §4).
    const nameField = screen.getByDisplayValue("Perfumes");
    await user.clear(nameField);
    await user.type(nameField, "Perfumes e cuidados");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText("Perfumes e cuidados")).toBeInTheDocument();
    expect(requests).toHaveLength(1);
    expect(requests[0].method).toBe("PUT");
    expect(await requests[0].json()).toEqual({ name: "Perfumes e cuidados" });
  });

  // A shared/bookmarked URL can outlive the list it points at: the backend
  // answers the truth for a past-the-end page rather than clamping. The
  // panel must offer the way back instead of a dead empty screen (the #42
  // review's finding).
  test("recovers a URL past the last page with a one-click way back", async () => {
    signInAs("ADMIN", "admin@bms.local");
    window.history.replaceState({}, "", "/categories?page=5");
    server.use(
      http.get(CATEGORIES_URL, ({ request }) => {
        const page = new URL(request.url).searchParams.get("page");
        return HttpResponse.json(
          page === "5"
            ? makeEnvelope<Category>([], { page: 5, total: 47, totalPages: 3 })
            : makeEnvelope<Category>(
                [{ id: 46, name: "Zíngibers", active: true }],
                { page: 2, size: 20, total: 47, totalPages: 3 },
              ),
        );
      }),
    );
    const user = userEvent.setup();

    renderPage();

    // 1-based position of the page the URL asked for — the URL stays 0-based.
    expect(await screen.findByText("Page 6 of 3")).toBeInTheDocument();
    expect(screen.queryByText("Nothing here yet.")).toBeNull();

    await user.click(screen.getByRole("button", { name: "Go to last page" }));

    expect(await screen.findByText("Zíngibers")).toBeInTheDocument();
    expect(window.location.search).toBe("?page=2");
  });
});
