# Business rules

Enforceable rules. Glossary definitions live in `GLOSSARY.md`; table shapes in `docs/database/database-design.md`.

## 1. Order state machine

```text
DRAFT → CONFIRMED → PROCESSING → COMPLETED
  ↓         ↓                          ↕ (return op)
CANCELLED CANCELLED                   RETURNED
```

- DRAFT: freely editable (items, quantities, customer). No stock or money effect.
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
| Categories | ✓ | — | — |
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

Enforced by `@PreAuthorize` on write services; URL layer is authenticated-only. Two arguable cells kept open: receiving POs and cancel-CONFIRMED for EMPLOYEE if the clerk doubles as warehouse.

## 5. Deactivation and drafts (Q28)

- Deactivated product/customer/supplier: blocked in all *new* documents; blocks **confirm** if sitting in a DRAFT; in-flight flows complete; history never rewired.
- Deactivated **user**: the password stops authenticating — login answers the same indistinguishable `401` as any bad login (never "account disabled"). A JWT issued *before* deactivation keeps working until it expires: with no token store (ADR 0015), deactivation revokes the next login, not the current session. Users are deactivated, never deleted.
- `document` unique per party table (DB); `email` not unique. SKU unique. Category name unique.
