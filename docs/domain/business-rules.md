# Business rules

Enforceable rules. Glossary definitions live in `GLOSSARY.md`; table shapes in `docs/database/database-design.md`.

## 1. Order state machine

```text
DRAFT → CONFIRMED → PROCESSING → COMPLETED
  ↓         ↓                          ↕ (return op)
CANCELLED CANCELLED                   RETURNED
```

- DRAFT: freely editable (items, quantities, customer). Every save re-prices each line from the product (GLOSSARY "Price"); CONFIRM locks the price and applies stock. No stock or money effect while DRAFT.
- CONFIRMED: validates active customer, all products active and present, `available ≥ qty` per line (re-checked under product-row lock). Creates reservations. Cancellable → releases reservations, zero ledger rows.
- PROCESSING-entry: releases reservations + writes SALE movements **atomically**. Writes `unitCost` snapshot per item (current `product.cost`).
- PROCESSING: not cancellable.
- COMPLETED: human-confirmed handover; issues the invoice exactly once (sequential, immutable).
- Cancel after delivery does not exist; the operation is **return** (full-order v1): +RETURN movement per product → RETURNED + CreditNote (+ refund row if overpaid).
- No hard deletes of orders, ever.

## 2. Inventory and cost

- Movements append-only; types SALE/RETURN reference orders, PURCHASE references POs, ADJUSTMENT/DAMAGE require a reason and are never client-writable as SALE/PURCHASE/RETURN.
- `available = on-hand − Σ open reservations`. No reservation expiry.
- `stock` column mirrored with each movement; `CHECK (stock >= 0)` at DB level; integration test asserts column == SUM(movements).
- Weighted average on receipt: `avg′ = (stock × avg + qty × cost) / (stock + qty)`; stock 0 → `avg′ = cost`. SALE does not touch the average; sales returns re-enter at the **current** average (documented simplification vs FIFO).
- minimumStock advisory only. Full receipts only (v1).

## 3. Money

- No PAID state on orders. Invoice status OPEN/PARTIAL/PAID/CREDITED derived.
- Partial payments both directions; methods PIX / transfer / boleto / cash.
- Per-customer/per-supplier `paymentTermsDays` default 30; due = issue + terms. Overdue is display-only.
- FinancialTransaction = cash movement only; corrections = VOID + re-enter (including opex). VOID rows excluded from every balance.
- Opex categories: RENT, SALARIES, UTILITIES, MARKETING, FEES, OTHER.
- Profit = Σ(unitPrice − unitCost) × qty − opex. Dashboard shows invoiced revenue **and** cash collected with outstanding as the bridge.

## 4. Permissions (Q33 matrix)

| Action | ADMIN | MANAGER | EMPLOYEE |
|---|:-:|:-:|:-:|
| Create/edit customer | ✓ | ✓ | ✓ |
| Deactivate customer | ✓ | ✓ | — |
| Create/edit/deactivate product | ✓ | ✓ | — |
| Manage categories (create/edit/status) | ✓ | — | — |
| Create/edit supplier | ✓ | ✓ | — |
| Order flow through COMPLETED, cancel CONFIRMED | ✓ | ✓ | ✓ |
| Return (moves money) | ✓ | ✓ | — |
| Record customer payment | ✓ | ✓ | — |
| PO create/edit, receive, cancel | ✓ | ✓ | — |
| Inventory adjustments | ✓ | ✓ | — |
| Finance view | ✓ | ✓ | — |
| Opex entries | ✓ | — | — |
| Users | ✓ | — | — |
| Dashboard / reports / CSV | ✓ | ✓ | ✓ |

Enforced by `@PreAuthorize` on write services; URL layer is authenticated-only. The Manage-categories row governs *managing* the vocabulary; listing it (the read side) is open to every signed-in role, since MANAGER and EMPLOYEE need it on the product forms of tickets #6/#7 — the supplier vocabulary likewise (it is the product form's supplier dropdown), so `/suppliers` is readable by every role and only its writes are gated — and `/customers` likewise (a clerk opens a sale for the walk-in party; only its deactivation is gated). Two arguable cells kept open: receiving POs and cancel-CONFIRMED for EMPLOYEE if the clerk doubles as warehouse.

## 5. Deactivation and drafts (Q28)

- Deactivated product/customer/supplier: blocked in all *new* documents; blocks **confirm** if sitting in a DRAFT; in-flight flows complete; history never rewired.
- Deactivated **user**: the password stops authenticating — login answers the same indistinguishable `401` as any bad login (never "account disabled"). A JWT issued *before* deactivation keeps working until it expires: with no token store (ADR 0015), deactivation revokes the next login, not the current session. Users are deactivated, never deleted.
- `document` unique per party table (DB) — `customers` and `suppliers` alike (V7, V6); `email` not unique. A party's document is a CPF or a CNPJ (business-rules §4 rows "Create/edit customer" / "Create/edit supplier"): stored in **canonical form** — digits only, punctuation is presentation, so `11.222.333/0001-81` and `11222333000181` are one party —, exactly 11 or 14 digits (DB CHECK) whose **check digits** add up (entity refusal → 422, ADR 0017: a modulus-11 checksum is arithmetic, not a constraint Postgres can express). The unique index is on the exact digits (nothing to case-fold), so two spellings of one document collide there → 409, with the canonical spelling quoted. SKU unique, **case-insensitive** (unique index on `lower(sku)`, V4) and stored in **canonical form** — trimmed + lowercase (entity + DB CHECK since V5; GLOSSARY "Canonical form"). Category name unique, **case-insensitive** (unique index on `lower(name)`), 1–120 chars (DB CHECK since V4), stored trimmed (entity + DB CHECK since V5).
- Product write guards (entity refusals → 422, ADR 0017): price > 0, cost ≥ 0, minimum stock ≥ 0, and money at most **two decimal places** — the `numeric(12,2)` column would silently round a finer value on the way in, so the domain refuses it instead.
