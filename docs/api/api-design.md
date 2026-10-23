# API design

REST, no `/v1` prefix (deliberate — single-client portfolio API; version when a second consumer appears). Cookie auth. springdoc → live Swagger UI.

## Conventions

- Paged envelope `{items, page, size, total, totalPages}` — never leak Spring's `Page`. `size ≤ 100`. Sort whitelist per resource.
- Filters over nested collections: `GET /orders?customerId=`, `GET /orders?status=`. No `/customers/{id}/orders` duplication.
- Deactivation = `PUT /{resource}/{id}/status` with `{active}` everywhere (categories, customers, products, suppliers, users).
- Manual DTO mapping (no MapStruct at this scale). DTOs validated with `@Valid`; domain refusals surface as 409/422 (see Errors).
- Numbers `ORD|PO|INV-YYYY-NNNN` generated server-side.

## Endpoints

```text
ops:        GET /actuator/health — 200 {"status":"UP"} plus per-component detail
            (show-components=always; includes db). Only health is exposed.
auth:       POST /api/auth/login  POST /api/auth/logout  GET /api/auth/me
            PUT /api/auth/password (needs current password)
users:      CRUD (ADMIN) + PUT /users/{id}/status
categories: CRUD (ADMIN) + PUT /categories/{id}/status — list open to any signed-in role; name 1–120 chars, unique ignoring case
customers: CRUD + status + `?search=` (ILIKE on name, and on the document
  normalized to digits — same predicate as suppliers) + pagination — list open to
  any signed-in role, create/edit ADMIN/MANAGER/EMPLOYEE, deactivation
  ADMIN/MANAGER (business-rules §4 — the widest write cell in the matrix).
  document travels in canonical form — digits only, punctuation stripped on the
  way in —, is unique (409; the sentence quotes the canonical spelling), and
  must be 11 digits (CPF) or 14 (CNPJ) whose check digits add up (422 — ADR
  0017). email optional and never unique; payment terms optional — omitted
  means the stored default 30, and the response echoes what was stored.
suppliers: CRUD + status + `?search=` (ILIKE on name, and on the document normalized to
  digits — the punctuated CPF/CNPJ a person types still finds the row stored as digits) +
  pagination — list open to any signed-in role, writes ADMIN/MANAGER (business-rules §4).
  document travels in canonical form — digits only, punctuation stripped on the way in —,
  is unique (409; the sentence quotes the canonical spelling), and must be 11 digits (CPF)
  or 14 (CNPJ) whose check digits add up (422 — ADR 0017 puts the arithmetic on the
  entity). email optional and never unique; payment terms optional — omitted means the
  stored default 30, and the response echoes what was stored.
products: CRUD + status + `?search=` (ILIKE on name and sku) + pagination — list open to
  any signed-in role, writes ADMIN/MANAGER (business-rules §4). sku unique ignoring case
  (409) and canonicalized on write — trimmed + lowercase, and the response echoes the
  canonical form, not the spelling posted (GLOSSARY "Canonical form"); category required —
  unknown categoryId is a 404; price > 0, cost ≥ 0,
  minimum stock ≥ 0, and money at most two decimal places (422 — the numeric(12,2)
  column would round a finer value). supplierId optional — a product may be listed before
  anyone has decided who supplies it; an unknown id is a 404 resolved *before* the insert
  (never a foreign-key 500), and the response echoes supplierId + supplierName (LEFT join,
  null on both sides when there is none).
orders:
  GET /api/orders  GET /api/orders/{id}  POST /api/orders
  PUT /api/orders/{id} (draft only)  — no DELETE, ever
  POST /api/orders/{id}/confirm | /start-processing | /complete | /cancel | /return
inventory:
  GET /api/inventory             (stock + available + low-stock flags)
  GET /api/inventory/movements
  POST /api/inventory/adjustments  {productId, qty, type: ADJUSTMENT|DAMAGE, reason!}
purchases:
  GET|POST /api/purchases  GET /api/purchases/{id}  PUT (draft only)
  POST /api/purchases/{id}/order | /receive | /complete | /cancel
finance:
  GET /api/finance/invoices  GET /api/finance/invoices/{id}
  GET /api/finance/payables  GET /api/finance/payables/{id}
  POST /api/finance/invoices/{id}/payments   (INCOME row)
  POST /api/finance/payables/{id}/payments   (EXPENSE row)
  GET /api/finance/transactions
  POST /api/finance/expenses (ADMIN)  POST /api/finance/transactions/{id}/void (ADMIN)
  # credit notes exist only via POST /orders/{id}/return — no direct endpoint
dashboard:
  GET /api/dashboard?from&to   # metrics + daily series + top-products + low-stock + recent — one payload
reports:
  GET /api/reports/sales?from&to      GET /api/reports/purchases?from&to
  Accept: text/csv → CSV export
```

## Errors — RFC 9457

One `@RestControllerAdvice` mapping the `BusinessException` hierarchy to `ProblemDetail`:

| Status | Meaning | Example |
|---|---|---|
| 400 + `fieldErrors` | DTO shape invalid | price = "abc"; blank category name |
| 401 / 403 | no/bad token / wrong role | employee posts an adjustment |
| 404 | unknown id | `/orders/999` |
| 409 | conflict with state or stock | cancel PROCESSING; insufficient available; duplicate SKU; duplicate category name; duplicate supplier document |
| 422 | business rule refuses | cost < 0; price = 0.001 (cents scale); a supplier document that is not a CPF/CNPJ |
| 500 | unexpected, no stack leak | bug |

Frontend: `fieldErrors` → the field they name; a refusal's `detail` sentence (the 409/422 rows above) → inline where the user was acting; anything else → toast/banner.

**Exception — `POST /api/auth/login` never returns 400.** A missing or malformed credential field answers the same `401` + `problem+json` as wrong credentials: a distinct "invalid shape" reply on a credential field tells a caller which half of which account exists (user enumeration), and a null password must not become a 500. `fieldErrors` applies to every other DTO.
