# API design

REST, no `/v1` prefix (deliberate — single-client portfolio API; version when a second consumer appears). Cookie auth. springdoc → live Swagger UI.

## Conventions

- Paged envelope `{items, page, size, total, totalPages}` — never leak Spring's `Page`. `size ≤ 100`. Sort whitelist per resource.
- Filters over nested collections: `GET /orders?customerId=`, `GET /orders?status=`. No `/customers/{id}/orders` duplication.
- Deactivation = `PUT /{resource}/{id}/status` with `{active}` everywhere (customers, products, suppliers, users).
- Manual DTO mapping (no MapStruct at this scale). DTOs validated with `@Valid`; domain refusals surface as 409/422 (see Errors).
- Numbers `ORD|PO|INV-YYYY-NNNN` generated server-side.

## Endpoints

```text
ops:        GET /actuator/health — 200 {"status":"UP"} plus per-component detail
            (show-components=always; includes db). Only health is exposed.
auth:       POST /api/auth/login  POST /api/auth/logout  GET /api/auth/me
            PUT /api/auth/password (needs current password)
users:      CRUD (ADMIN) + PUT /users/{id}/status
categories: CRUD (ADMIN)
customers / products / suppliers: CRUD + status + search/filter/pagination
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
| 400 + `fieldErrors` | DTO shape invalid | price = "abc" |
| 401 / 403 | no/bad token / wrong role | employee posts an adjustment |
| 404 | unknown id | `/orders/999` |
| 409 | conflict with state or stock | cancel PROCESSING; insufficient available; duplicate SKU |
| 422 | business rule refuses | cost < 0 |
| 500 | unexpected, no stack leak | bug |

Frontend: `fieldErrors` → inline form errors; everything else → toast/banner.
