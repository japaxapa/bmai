import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, test, vi } from "vitest";
import { server } from "@/test/server";
import { makeEnvelope } from "@/test/envelope";
import { DEFAULT_API_BASE_URL } from "@/lib/api/base-url";
import { AppProviders } from "@/providers";
import { SuppliersPanel } from "@/app/(dashboard)/suppliers/_components/suppliers-panel";
import { Toaster } from "@/components/ui/sonner";

// jsdom never mounts the app router, so anything calling useRouter() /
// useSearchParams() would blow up without this double.
vi.mock("next/navigation", () => import("@/test/next-navigation"));

const SUPPLIERS_URL = `${DEFAULT_API_BASE_URL}/api/suppliers`;
const ME_URL = `${DEFAULT_API_BASE_URL}/api/auth/me`;

type Role = "ADMIN" | "MANAGER" | "EMPLOYEE";
type Supplier = {
  id: number;
  name: string;
  document: string;
  email: string | null;
  paymentTermsDays: number;
  active: boolean;
};

/**
 * The canonical spelling the API stores (GLOSSARY "Canonical form": digits
 * only) and the punctuated one the UI must show — same row, two spellings.
 * Both documents are valid: 11222333000181 is a CNPJ whose check digits add
 * up, 12345678909 a CPF (the domain refuses anything else with a 422).
 */
const CNPJ = "11222333000181";
const CNPJ_DISPLAY = "11.222.333/0001-81";
const CPF = "12345678909";

const ACME_ROW: Supplier = {
  id: 1,
  name: "Acme Distribuidora",
  document: CNPJ,
  email: "compras@acme.local",
  paymentTermsDays: 30,
  active: true,
};

function signInAs(role: Role, email: string) {
  server.use(http.get(ME_URL, () => HttpResponse.json({ email, role })));
}

// The page is an async server component (#44: it seeds the list during the
// HTML render), which jsdom cannot render — the panel island is the test seam
// (testing-strategy §4); the seed itself is covered in seed.test.tsx.
function renderPage() {
  return render(
    <AppProviders>
      <SuppliersPanel />
    </AppProviders>,
  );
}

describe("suppliers panel", () => {
  // Each render gets a fresh QueryClient (query-provider's test branch,
  // ADR 0020), so no test can be answered from another test's cache.
  test("lists the suppliers the API returns, spelling the document for reading", async () => {
    signInAs("ADMIN", "admin@bms.local");
    server.use(
      http.get(SUPPLIERS_URL, () =>
        HttpResponse.json(
          makeEnvelope([
            ACME_ROW,
            {
              id: 2,
              name: "Café do Vale",
              document: CPF,
              email: null,
              paymentTermsDays: 45,
              active: false,
            },
          ]),
        ),
      ),
    );

    renderPage();

    expect(await screen.findByText("Acme Distribuidora")).toBeInTheDocument();
    // Format on display: the stored digits are the canonical form, the box a
    // person reads carries the punctuation their eyes expect.
    expect(screen.getByText(CNPJ_DISPLAY)).toBeInTheDocument();
    expect(screen.getByText("123.456.789-09")).toBeInTheDocument();
    expect(screen.getByText("Net 30")).toBeInTheDocument();
    expect(screen.getByText("Net 45")).toBeInTheDocument();
    // GLOSSARY "Deactivated": the row stays in the list, marked — never gone.
    expect(screen.getByText("Deactivated")).toBeInTheDocument();
  });

  test("creating a supplier masks the document, posts digits only and lets the server default the terms", async () => {
    signInAs("ADMIN", "admin@bms.local");
    let created = false;
    const requests: Request[] = [];
    server.use(
      http.get(SUPPLIERS_URL, () =>
        HttpResponse.json(
          created
            ? makeEnvelope([ACME_ROW])
            : makeEnvelope<Supplier>([]),
        ),
      ),
      http.post(SUPPLIERS_URL, async ({ request }) => {
        requests.push(request);
        created = true;
        return HttpResponse.json(ACME_ROW, { status: 201 });
      }),
    );
    const user = userEvent.setup();

    renderPage();
    await screen.findByRole("button", { name: "Create supplier" });

    await user.type(screen.getByLabelText("Name"), ACME_ROW.name);
    await user.type(screen.getByLabelText("Document"), CNPJ);
    await user.type(screen.getByLabelText("Email"), "compras@acme.local");

    // The mask is presentation: the box a person typed 14 digits into shows
    // the CNPJ punctuation, and the switch happened at the 12th digit (the
    // CPF mask could never hold 14).
    expect(screen.getByLabelText("Document")).toHaveValue(CNPJ_DISPLAY);

    await user.click(screen.getByRole("button", { name: "Create supplier" }));

    expect(await screen.findByText("Acme Distribuidora")).toBeInTheDocument();
    expect(requests).toHaveLength(1);
    expect(requests[0].method).toBe("POST");
    expect(requests[0].credentials).toBe("include");
    const body = (await requests[0].json()) as Record<string, unknown>;
    // Canonical end to end: the punctuation never reaches the unique index.
    expect(body).toEqual({
      name: ACME_ROW.name,
      document: CNPJ,
      email: "compras@acme.local",
    });
    // An empty terms box is not a 30 the UI made up — it stays *out* of the
    // body, and 30 is the server's own default (business-rules §3).
    expect(body).not.toHaveProperty("paymentTermsDays");
  });

  // The Toaster is deliberately NOT mounted in this file: the AC says the
  // duplicate document is "rendered inline in the UI, never a raw failure",
  // and a toast-only implementation must not be able to satisfy this test.
  test("prints the conflict detail inline when the document is already taken", async () => {
    signInAs("ADMIN", "admin@bms.local");
    server.use(
      http.get(SUPPLIERS_URL, () =>
        HttpResponse.json(makeEnvelope([ACME_ROW])),
      ),
      http.post(SUPPLIERS_URL, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Conflict",
            status: 409,
            detail: `A supplier with document ${CNPJ} already exists.`,
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
    await screen.findByText("Acme Distribuidora");

    await user.type(screen.getByLabelText("Name"), "Outra Distribuidora");
    await user.type(screen.getByLabelText("Document"), CNPJ);
    await user.click(screen.getByRole("button", { name: "Create supplier" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      `A supplier with document ${CNPJ} already exists.`,
    );
    // The refusal stayed a refusal: no row ever took that name. (The typed
    // value in an input is not text content — an exact match can only be a row.)
    expect(screen.queryByText("Outra Distribuidora")).toBeNull();
  });

  // The domain rule (ADR 0017: check digits on the entity) answers 422, and
  // its sentence — like every other refusal — reaches the reader inline.
  test("prints the domain's 422 when the check digits do not add up", async () => {
    signInAs("ADMIN", "admin@bms.local");
    server.use(
      http.get(SUPPLIERS_URL, () =>
        HttpResponse.json(makeEnvelope([ACME_ROW])),
      ),
      http.post(SUPPLIERS_URL, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Unprocessable Entity",
            status: 422,
            detail: "Document check digits do not match.",
          },
          {
            status: 422,
            headers: { "Content-Type": "application/problem+json" },
          },
        ),
      ),
    );
    const user = userEvent.setup();

    renderPage();
    await screen.findByText("Acme Distribuidora");

    // 14 digits — it passes every shape check the form owns and reaches the
    // one rule only the domain can judge.
    await user.type(screen.getByLabelText("Name"), "Digitos errados");
    await user.type(screen.getByLabelText("Document"), "11222333000182");
    await user.click(screen.getByRole("button", { name: "Create supplier" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Document check digits do not match.",
    );
    expect(screen.queryByText("Digitos errados")).toBeNull();
  });

  // The form's zod gate is courtesy only: a value it lets through that the
  // server refuses as a 400 field error must land on the field it names —
  // the server's message is the one the box shows.
  test("shows the API's field message on the field it names", async () => {
    signInAs("ADMIN", "admin@bms.local");
    server.use(
      http.get(SUPPLIERS_URL, () =>
        HttpResponse.json(makeEnvelope([ACME_ROW])),
      ),
      http.post(SUPPLIERS_URL, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Bad Request",
            status: 400,
            detail: "Validation failed",
            fieldErrors: {
              name: ["Name is required."],
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
    await screen.findByText("Acme Distribuidora");

    // Whitespace is a non-empty string, so it clears the form's min(1) — the
    // trim (and therefore the refusal) belongs to the server.
    await user.type(screen.getByLabelText("Name"), "   ");
    await user.type(screen.getByLabelText("Document"), CPF);
    await user.click(screen.getByRole("button", { name: "Create supplier" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Name is required.",
    );
    expect(screen.getByLabelText("Name")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });

  test("hides the write controls from a signed-in employee", async () => {
    signInAs("EMPLOYEE", "clerk@bms.local");
    server.use(
      http.get(SUPPLIERS_URL, () =>
        HttpResponse.json(makeEnvelope([ACME_ROW])),
      ),
    );

    renderPage();

    // UI hides by role, UX only — the backend refuses independently
    // (frontend-architecture §State).
    expect(await screen.findByText("Acme Distribuidora")).toBeInTheDocument();
    expect(screen.queryByLabelText("Name")).toBeNull();
    expect(
      screen.queryByRole("button", { name: "Create supplier" }),
    ).toBeNull();
  });

  test("shows the write controls to a manager (business-rules §4)", async () => {
    signInAs("MANAGER", "supervisor@bms.local");
    server.use(
      http.get(SUPPLIERS_URL, () =>
        HttpResponse.json(makeEnvelope([ACME_ROW])),
      ),
    );

    renderPage();

    expect(
      await screen.findByRole("button", { name: "Create supplier" }),
    ).toBeInTheDocument();
  });

  test("switches a supplier off from its row and says so", async () => {
    signInAs("ADMIN", "admin@bms.local");
    let active = true;
    const requests: Request[] = [];
    server.use(
      http.get(SUPPLIERS_URL, () =>
        HttpResponse.json(makeEnvelope([{ ...ACME_ROW, active }])),
      ),
      http.put(`${SUPPLIERS_URL}/1/status`, async ({ request }) => {
        requests.push(request);
        active = false;
        return HttpResponse.json({ ...ACME_ROW, active: false });
      }),
    );
    const user = userEvent.setup();

    renderPage();
    await screen.findByText("Acme Distribuidora");

    await user.click(
      screen.getByRole("button", { name: "Deactivate Acme Distribuidora" }),
    );

    expect(
      await screen.findByRole("button", {
        name: "Reactivate Acme Distribuidora",
      }),
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
    server.use(
      http.get(SUPPLIERS_URL, () =>
        HttpResponse.json(makeEnvelope([ACME_ROW])),
      ),
      http.put(`${SUPPLIERS_URL}/1/status`, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Not Found",
            status: 404,
            // The real sentence (SupplierNotFoundException): the mock must
            // pin the contract the server actually sends, not a fiction the
            // toast happens to render.
            detail: "No supplier with id 1",
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
        <SuppliersPanel />
      </AppProviders>,
    );
    await screen.findByText("Acme Distribuidora");

    await user.click(
      screen.getByRole("button", { name: "Deactivate Acme Distribuidora" }),
    );

    // The sentence reached the user...
    expect(await screen.findByText("No supplier with id 1")).toBeInTheDocument();
    // ...and the create form's alert stayed empty: a status failure must not
    // masquerade as a document conflict.
    expect(screen.queryByRole("alert")).toBeNull();
  });

  test("edits a supplier through the same form, seeded from the row", async () => {
    signInAs("ADMIN", "admin@bms.local");
    // 45 rather than the fixture's born-30, so a form that seeded the box
    // with its own default instead of the row's would fail below.
    let current: Supplier = { ...ACME_ROW, paymentTermsDays: 45 };
    const requests: Request[] = [];
    server.use(
      http.get(SUPPLIERS_URL, () =>
        HttpResponse.json(makeEnvelope([current])),
      ),
      http.put(`${SUPPLIERS_URL}/1`, async ({ request }) => {
        // An undici Request body is single-read: keep an unread copy for the
        // assertions below and consume the original to track the edit.
        requests.push(request.clone());
        const body = (await request.json()) as Partial<Supplier>;
        current = { ...current, ...body };
        return HttpResponse.json(current);
      }),
    );
    const user = userEvent.setup();

    renderPage();
    await screen.findByText("Acme Distribuidora");

    await user.click(
      screen.getByRole("button", { name: "Edit Acme Distribuidora" }),
    );

    // Every box holds the row's own value the moment the form opens — the
    // document spelled for reading, the terms the server stored (45 here, so
    // a form that silently fell back to the born-30 default would fail).
    expect(screen.getByDisplayValue("Acme Distribuidora")).toBeInTheDocument();
    expect(screen.getByDisplayValue(CNPJ_DISPLAY)).toBeInTheDocument();
    expect(screen.getByDisplayValue("compras@acme.local")).toBeInTheDocument();
    expect(screen.getByDisplayValue("45")).toBeInTheDocument();

    const nameField = screen.getByDisplayValue("Acme Distribuidora");
    await user.clear(nameField);
    await user.type(nameField, "Acme Alimentos");
    const termsField = screen.getByDisplayValue("45");
    await user.clear(termsField);
    await user.type(termsField, "60");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText("Acme Alimentos")).toBeInTheDocument();
    expect(requests).toHaveLength(1);
    expect(requests[0].method).toBe("PUT");
    // The whole shape again — an edit cannot silently blank a field left out.
    expect(await requests[0].json()).toEqual({
      name: "Acme Alimentos",
      document: CNPJ,
      email: "compras@acme.local",
      paymentTermsDays: 60,
    });
  });

  // filter state in the URL (testing-strategy §4, ADR 0019): a search is
  // state that must survive a reload and stay shareable.
  test("searches the API as the term is typed, keeps it in the URL and re-roots the page", async () => {
    signInAs("ADMIN", "admin@bms.local");
    window.history.replaceState({}, "", "/suppliers?page=1");
    const requested: string[] = [];
    server.use(
      http.get(SUPPLIERS_URL, ({ request }) => {
        requested.push(new URL(request.url).search);
        return HttpResponse.json(
          new URL(request.url).searchParams.get("search") === "acme"
            ? makeEnvelope([ACME_ROW])
            : makeEnvelope([]),
        );
      }),
    );
    const user = userEvent.setup();

    renderPage();
    await screen.findByRole("textbox", { name: "Search" });

    await user.type(screen.getByRole("textbox", { name: "Search" }), "acme");

    // Only the refetch carrying the term can bring the matching row in.
    expect(await screen.findByText("Acme Distribuidora")).toBeInTheDocument();
    // The request carried the term, on page 0 — the old offset belongs to
    // the old result set...
    expect(requested.at(-1)).toContain("search=acme");
    expect(requested.at(-1)).toContain("page=0");
    // ...and the URL keeps the term (shareable), without the stale page.
    expect(window.location.search).toBe("?search=acme");
  });
});
