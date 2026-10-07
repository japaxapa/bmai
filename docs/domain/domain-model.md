# Domain model

Single context (one `GLOSSARY.md` at root). Aggregates, states, and invariants below. Field-level detail lives in `docs/database/database-design.md`; enforced rules in `docs/domain/business-rules.md`.

## Aggregates

- **Customer** — profile + `paymentTermsDays` (default 30) + status. Referenced by orders/invoices; never cascade-deleted.
- **Supplier** — mirror of customer on the buy side; `paymentTermsDays` default 30.
- **Category** — ADMIN vocabulary; product requires one.
- **Product** — sku (unique), price (>0), cost (weighted avg), stock mirror, minimumStock (advisory), category, supplier, status.
- **Sales order (+ items)** — customer, items (product, qty, `unitPrice` locked at CONFIRMED, `unitCost` written at PROCESSING), total (computed). States: DRAFT, CONFIRMED, PROCESSING, COMPLETED, CANCELLED, RETURNED.
- **Reservation** — `(orderId, productId, qty)` rows alive while CONFIRMED. Not a ledger row.
- **Inventory movement** — append-only: product, signed qty, type, reason (for ADJUSTMENT/DAMAGE), reference (order or PO), timestamp.
- **Purchase order (+ items)** — supplier, items, total. States: DRAFT, ORDERED, RECEIVED, COMPLETED, CANCELLED.
- **Invoice** — 1:1 with order, issued at COMPLETED. Number, issue/due dates, total. Derived status.
- **Payable** — 1:1 with PO, born at RECEIVED. Derived status.
- **CreditNote** — issued by the return operation against an invoice.
- **FinancialTransaction** — one money movement: INCOME (customer payment, with method) / EXPENSE (supplier payment, opex, refund). POSTED/VOID.
- **User** — email (unique, login identity), password hash, role, active flag.

## Key relationships

- Order → Customer (N:1). OrderItem → Product (N:1). Product → Category, Supplier (N:1).
- Invoice → Order (1:1). CreditNote → Invoice (N:1). FinancialTransaction → Invoice | Payable (nullable, at most one).
- Movement → Order (SALE/RETURN) | PurchaseOrder (PURCHASE) | reason (ADJUSTMENT/DAMAGE).
- Payable → PurchaseOrder (1:1).

## Ownership and lifecycle

- Parties, products, orders, and money are **never hard-deleted**; deactivation / CANCELLED / VOID are the only exits.
- Cancellation always precedes invoicing; only returns happen after (Q18 rule).
- Money owed is derived: `total − Σpayments − ΣcreditNotes`. No stored balance column.
