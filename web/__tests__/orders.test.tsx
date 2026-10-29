import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, test, vi } from "vitest";
import { server } from "@/test/server";
import { makeEnvelope } from "@/test/envelope";
import { DEFAULT_API_BASE_URL } from "@/lib/api/base-url";
import { AppProviders } from "@/providers";
import { OrdersPanel } from "@/app/(dashboard)/orders/_components/orders-panel";
import { OrderEditor } from "@/features/orders/components/order-editor";
import { push } from "@/test/next-navigation";

// jsdom never mounts the app router, so anything calling useRouter() /
// useSearchParams() would blow up without this double.
vi.mock("next/navigation", () => import("@/test/next-navigation"));

const ORDERS_URL = `${DEFAULT_API_BASE_URL}/api/orders`;
const CUSTOMERS_URL = `${DEFAULT_API_BASE_URL}/api/customers`;
const PRODUCTS_URL = `${DEFAULT_API_BASE_URL}/api/products`;

/** The parties and products the editor's dropdowns are stocked from. */
const CUSTOMER = {
  id: 7,
  name: "Mercado Acme",
  document: "11222333000181",
  email: null,
  paymentTermsDays: 30,
  active: true,
};

const COFFEE = {
  id: 11,
  sku: "cafe-1",
  name: "Café Torrado 1kg",
  description: null,
  price: 12.5,
  cost: 8,
  minimumStock: 0,
  stock: 10,
  categoryId: 1,
  categoryName: "Mercearia",
  supplierId: null,
  supplierName: null,
  active: true,
};

const SUGAR = {
  ...COFFEE,
  id: 12,
  sku: "acucar-1",
  name: "Açúcar Refinado 1kg",
  price: 4.1,
  cost: 2.5,
};

type OrderSummary = {
  id: number;
  number: string;
  customerId: number;
  customerName: string;
  status: "DRAFT" | "CONFIRMED" | "PROCESSING" | "COMPLETED" | "CANCELLED" | "RETURNED";
  total: number;
  createdAt: string;
};

const DRAFT_ROW: OrderSummary = {
  id: 1,
  number: "ORD-2026-0001",
  customerId: 7,
  customerName: "Mercado Acme",
  status: "DRAFT",
  total: 37.3,
  createdAt: "2026-10-27T12:00:00Z",
};

const CONFIRMED_ROW: OrderSummary = {
  id: 2,
  number: "ORD-2026-0002",
  customerId: 7,
  customerName: "Padaria Vale",
  status: "CONFIRMED",
  total: 12.3,
  createdAt: "2026-10-26T12:00:00Z",
};

/** The detail an edit route seeds from — same lines the create test builds. */
const DRAFT_DETAIL = {
  id: 1,
  number: "ORD-2026-0001",
  customerId: 7,
  customerName: "Mercado Acme",
  status: "DRAFT",
  items: [
    {
      id: 1,
      productId: 11,
      sku: "cafe-1",
      productName: "Café Torrado 1kg",
      quantity: 2,
      unitPrice: 12.5,
      subtotal: 25,
    },
    {
      id: 2,
      productId: 12,
      sku: "acucar-1",
      productName: "Açúcar Refinado 1kg",
      quantity: 3,
      unitPrice: 4.1,
      subtotal: 12.3,
    },
  ],
  total: 37.3,
  createdAt: "2026-10-27T12:00:00Z",
};

function renderPage() {
  return render(
    <AppProviders>
      <OrdersPanel />
    </AppProviders>,
  );
}

function renderEditor(orderId?: number) {
  return render(
    <AppProviders>
      <OrderEditor orderId={orderId} />
    </AppProviders>,
  );
}

/** The dropdowns' data: no editor test is about these two lists. */
function stockDropdowns() {
  return [
    http.get(CUSTOMERS_URL, () => HttpResponse.json(makeEnvelope([CUSTOMER]))),
    http.get(PRODUCTS_URL, () => HttpResponse.json(makeEnvelope([COFFEE, SUGAR]))),
  ];
}

/** Add a line and pick its product and quantity — the three-field dance. */
async function addLine(
  user: ReturnType<typeof userEvent.setup>,
  index: number,
  productId: number,
  quantity: number,
) {
  await user.click(screen.getByRole("button", { name: "Add item" }));
  await user.selectOptions(
    screen.getByLabelText(`Product ${index}`),
    String(productId),
  );
  const input = screen.getByLabelText(`Quantity ${index}`);
  await user.clear(input);
  await user.type(input, String(quantity));
}

describe("orders panel", () => {
  test("lists the orders with their number, customer, status and total", async () => {
    server.use(
      http.get(ORDERS_URL, () =>
        HttpResponse.json(makeEnvelope([DRAFT_ROW, CONFIRMED_ROW])),
      ),
    );

    renderPage();

    expect(await screen.findByText("ORD-2026-0001")).toBeInTheDocument();
    expect(screen.getByText("Mercado Acme")).toBeInTheDocument();
    expect(screen.getByText("DRAFT")).toBeInTheDocument();
    // BRL, the currency every money value is displayed in (requirements §4).
    expect(screen.getByText(/R\$\s*37,30/)).toBeInTheDocument();
    expect(screen.getByText(/R\$\s*12,30/)).toBeInTheDocument();

    // The way into the create form, and — only for the editable state — the
    // way into its editor. A CONFIRMED order offers no edit link: the API
    // would refuse it (409), so the UI must not offer it.
    expect(screen.getByRole("link", { name: "New order" })).toHaveAttribute(
      "href",
      "/orders/new",
    );
    expect(
      screen.getByRole("link", { name: "Edit ORD-2026-0001" }),
    ).toHaveAttribute("href", "/orders/1/edit");
    expect(
      screen.queryByRole("link", { name: "Edit ORD-2026-0002" }),
    ).toBeNull();
  });

  /**
   * AC: "Draft sales order creation, item add/remove, and totals shown in the
   * UI." The editor starts empty (a legal draft), items are added and
   * removed with their own buttons, and the running total is the sum of
   * quantity × the selected product's price — the same formula the backend
   * computes, shown while typing so the salesperson sees the order add up.
   */
  test("creates a draft, adding items and showing the running total", async () => {
    push.mockClear();
    const bodies: unknown[] = [];
    server.use(
      http.get(CUSTOMERS_URL, () =>
        HttpResponse.json(makeEnvelope([CUSTOMER])),
      ),
      http.get(PRODUCTS_URL, () =>
        HttpResponse.json(makeEnvelope([COFFEE, SUGAR])),
      ),
      http.post(ORDERS_URL, async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json(
          {
            id: 1,
            number: "ORD-2026-0001",
            customerId: 7,
            customerName: "Mercado Acme",
            status: "DRAFT",
            items: [],
            total: 37.3,
            createdAt: "2026-10-27T12:00:00Z",
          },
          { status: 201 },
        );
      }),
    );
    const user = userEvent.setup();

    render(
      <AppProviders>
        <OrderEditor />
      </AppProviders>,
    );

    // The dropdowns are stocked by their own queries, so wait for the option
    // rather than for the label: the label renders before the data arrives.
    await screen.findByRole("option", { name: "Mercado Acme" });
    await user.selectOptions(screen.getByLabelText("Customer"), "7");

    await user.click(screen.getByRole("button", { name: "Add item" }));
    await screen.findByRole("option", { name: "Café Torrado 1kg" });
    await user.selectOptions(screen.getByLabelText("Product 1"), "11");
    const firstQuantity = screen.getByLabelText("Quantity 1");
    await user.clear(firstQuantity);
    await user.type(firstQuantity, "2");
    // Read the total, not "R$ 25,00" anywhere: the line subtotal and the
    // running total are both 25,00 and a bare getByText would be ambiguous.
    expect(screen.getByTestId("order-total")).toHaveTextContent(/R\$\s*25,00/);

    await user.click(screen.getByRole("button", { name: "Add item" }));
    await user.selectOptions(screen.getByLabelText("Product 2"), "12");
    const secondQuantity = screen.getByLabelText("Quantity 2");
    await user.clear(secondQuantity);
    await user.type(secondQuantity, "3");

    // 2 × 12.50 + 3 × 4.10 = 37.30 — the same worked example the backend
    // totals in its unit test.
    expect(screen.getByTestId("order-total")).toHaveTextContent(/R\$\s*37,30/);

    await user.click(screen.getByRole("button", { name: "Save draft" }));

    await waitFor(() => expect(bodies).toHaveLength(1));
    expect(bodies[0]).toEqual({
      customerId: 7,
      items: [
        { productId: 11, quantity: 2 },
        { productId: 12, quantity: 3 },
      ],
    });
    expect(push).toHaveBeenCalledWith("/orders");
  });

  test("removing an item recomputes the total and drops it from the saved body", async () => {
    push.mockClear();
    const bodies: unknown[] = [];
    server.use(
      ...stockDropdowns(),
      http.post(ORDERS_URL, async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json(
          { ...DRAFT_DETAIL, items: [], total: 12.3 },
          { status: 201 },
        );
      }),
    );
    const user = userEvent.setup();

    renderEditor();

    await screen.findByRole("option", { name: "Mercado Acme" });
    await user.selectOptions(screen.getByLabelText("Customer"), "7");
    await addLine(user, 1, 11, 2);
    await addLine(user, 2, 12, 3);
    expect(screen.getByTestId("order-total")).toHaveTextContent(/R\$\s*37,30/);

    await user.click(screen.getByRole("button", { name: "Remove item 1" }));

    // The coffee is gone, so the sugar line is all that is left: 3 × 4.10.
    expect(screen.getByTestId("order-total")).toHaveTextContent(/R\$\s*12,30/);
    expect(screen.queryByLabelText("Product 2")).toBeNull();

    await user.click(screen.getByRole("button", { name: "Save draft" }));

    await waitFor(() => expect(bodies).toHaveLength(1));
    // PUT replaces the whole set, so the removed line must not travel.
    expect(bodies[0]).toEqual({
      customerId: 7,
      items: [{ productId: 12, quantity: 3 }],
    });
  });

  test("a server field error lands under the line it names", async () => {
    server.use(
      ...stockDropdowns(),
      http.post(ORDERS_URL, () =>
        HttpResponse.json(
          {
            fieldErrors: {
              "items[0].quantity": ["Quantity must be greater than zero."],
            },
          },
          { status: 400 },
        ),
      ),
    );
    const user = userEvent.setup();

    renderEditor();

    await screen.findByRole("option", { name: "Mercado Acme" });
    await user.selectOptions(screen.getByLabelText("Customer"), "7");
    await addLine(user, 1, 11, 2);

    await user.click(screen.getByRole("button", { name: "Save draft" }));

    // The API spells the nested path with brackets; react-hook-form parses it
    // into the same field the input is registered under.
    expect(
      await screen.findByText("Quantity must be greater than zero."),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Quantity 1")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });

  test("the edit route seeds the draft and PUTs the complete item set", async () => {
    push.mockClear();
    const puts: unknown[] = [];
    server.use(
      http.get(`${ORDERS_URL}/1`, () => HttpResponse.json(DRAFT_DETAIL)),
      ...stockDropdowns(),
      http.put(`${ORDERS_URL}/1`, async ({ request }) => {
        puts.push(await request.json());
        return HttpResponse.json({ ...DRAFT_DETAIL, total: 74.8 });
      }),
    );
    const user = userEvent.setup();

    renderEditor(1);

    // Seeded from GET /api/orders/1, not typed by the user.
    expect(await screen.findByLabelText("Quantity 1")).toHaveValue(2);
    expect(screen.getByLabelText("Quantity 2")).toHaveValue(3);
    expect(screen.getByTestId("order-total")).toHaveTextContent(/R\$\s*37,30/);

    const firstQuantity = screen.getByLabelText("Quantity 1");
    await user.clear(firstQuantity);
    await user.type(firstQuantity, "5");

    // 5 × 12.50 + 3 × 4.10 = 74.80, still priced from the live product.
    expect(screen.getByTestId("order-total")).toHaveTextContent(/R\$\s*74,80/);

    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(puts).toHaveLength(1));
    expect(puts[0]).toEqual({
      customerId: 7,
      items: [
        { productId: 11, quantity: 5 },
        { productId: 12, quantity: 3 },
      ],
    });
    expect(push).toHaveBeenCalledWith("/orders");
  });

  test("a draft confirmed in another tab shows the server's sentence inline", async () => {
    push.mockClear();
    server.use(
      http.get(`${ORDERS_URL}/1`, () => HttpResponse.json(DRAFT_DETAIL)),
      ...stockDropdowns(),
      http.put(`${ORDERS_URL}/1`, () =>
        HttpResponse.json(
          { detail: "Only DRAFT sales orders can be edited." },
          { status: 409 },
        ),
      ),
    );
    const user = userEvent.setup();

    renderEditor(1);

    await screen.findByLabelText("Quantity 1");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    // A 409 names no field, so it prints inline rather than under an input.
    expect(
      await screen.findByText("Only DRAFT sales orders can be edited."),
    ).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });
});
