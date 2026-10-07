# Requirements

## 1. Business context

- Fictional Brazilian B2B wholesale distributor (Q1). No field experience on the team — every rule must be researchable and explainable (Q2).
- Brazilian context (BRL, CPF/CNPJ-style `document`), code/docs/UI in English (Q3).
- Single location v1; multi-location is an explicit non-goal (Q17).
- Scale envelope: ~1,000–2,000 SKUs, 200–500 customers, 200–400 orders/month (Q10). Big enough that pagination, filtering, and indexes are real; small enough that partitioning is out of scope.

## 2. Users and roles

- ADMIN = owner. MANAGER = sales supervisor. EMPLOYEE = sales clerk (Q6).
- User management is ADMIN-only: a supervisor who can create users can create fake ones (Q6).
- The permission matrix in `docs/domain/business-rules.md` is derived from duties, not copied from an example (Q6, Q16, Q33):
  - EMPLOYEE = intake and the sales flow.
  - MANAGER = + operations, corrections, money-in.
  - ADMIN = + money-out, users, vocabulary.
- First ADMIN is seeded from env vars (`ADMIN_EMAIL` / `ADMIN_PASSWORD`). No self-registration. Change-own-password only (no reset email — needs mail infrastructure, backlog). Deactivate, never delete. Cannot deactivate self; cannot demote/deactivate the last active ADMIN (Q27).

## 3. Scope strategy

- All ~10 modules exist honestly but shallowly; depth on **orders, inventory, finance** (Q4, strategy b).
- Explicit cut-list (fallback to strategy c): partial receipts, partial returns, real supplier-return flow, multi-location, reservation expiry, per-order payment terms, dunning, password-reset email, tax/NF-e, FIFO cost layers, richer reports, refresh tokens, domain events, Modulith enforcement, optimistic UI, CI, rate limiting, free-tier deploy.
- Demo: seed script + README screenshots/GIF from day one; deployment is an optional last phase (Q5). Local-first; optional recipe Vercel (web) + Neon (Postgres) + Render free (API, sleeps) documented only (Q11).

## 4. Functional requirements

### Customers / Suppliers / Products / Categories
- CRUD + deactivate (never hard delete) + search + filter + pagination.
- Customer: order history, payment history. Supplier: product relationships, purchase history. Product: stock info, supplier + category association.
- `document` unique per party table (DB-level); `email` not unique (contact info, not identity) (Q28).
- Category: required on products, ADMIN-managed, name unique (Q26).
- Product `type` field cut — physical goods only.
- Business rules: price > 0, cost ≥ 0, SKU unique, stock never < 0 (DB-enforced), minimumStock ≥ 0 and advisory-only (Q28, brief).

### Sales orders
- DRAFT (editable scratchpad) → CONFIRMED (reserve) → PROCESSING (decrement) → COMPLETED (invoice). CANCELLED from DRAFT/CONFIRMED. RETURNED terminal via return op (Q8, Q12).
- No arbitrary transitions; the domain object refuses illegal ones (Q30).
- Confirm validates customer, products, and **available** stock under a product-row lock in one transaction (Q13).
- Deactivated product blocks confirm if sitting in a DRAFT; in-flight orders complete; history intact. Same pattern for deactivated parties (Q28).

### Inventory
- Append-only movement ledger (PURCHASE, SALE, ADJUSTMENT, RETURN, DAMAGE). Audit history is never rewritten (brief, Q12).
- Available = on-hand − reservations. No reservation expiry; cancel is the release valve (Q13).
- Adjustments/damage require a reason; never client-writable SALE/PURCHASE/RETURN types (Q12).

### Purchases
- DRAFT → ORDERED → RECEIVED → COMPLETED (human verify/close). CANCELLED from DRAFT/ORDERED (Q21).
- Full receipt only v1. Receipt writes +PURCHASE, updates weighted-average cost, opens the payable — atomically (Q20).
- Supplier-side removal after receipt = ADJUSTMENT with reason (Q12).

### Finance
- Credit sales, net-30 default per customer; partial payments; methods PIX / bank transfer / boleto / cash (Q7, Q14).
- Invoice issued once at COMPLETED: sequential (`INV-YYYY-NNNN`), immutable. Rule: *cancellation always precedes invoicing* (Q18).
- Payable born at RECEIVED; per-supplier terms default 30; same methods (Q22).
- FinancialTransaction = cash movement only (Q23). Obligations (Invoice/Payable) carry derived status OPEN/PARTIAL/PAID/CREDITED.
- Full-order returns only v1 → CreditNote; overpayment → refund EXPENSE row (Q24).
- Opex = manual categorized entries, ADMIN-only (Q15, Q16).
- Overdue = visual flag only (Q14).

### Dashboard / Reports
- One `GET /api/dashboard?from&to` (default last 30 days). Metrics: revenue invoiced, cash collected, outstanding, opex, gross margin, net profit, orders/units/AOV, SKUs/stock value/low-stock, recent orders, top products by revenue (Q19, Q25).
- Timestamps stored UTC, displayed −03:00 (Brazil has no DST). BRL formatting.
- Reports v1 = two filtered views (sales by period, purchases by period) + CSV export (Q25).

### Auth
- Stateless JWT in httpOnly cookie (SameSite=Lax, ~12h), BCrypt(10), roles in claims. CORS allow `http://localhost:3000` with credentials (Q32).
- Backend is the security boundary (`@PreAuthorize`); frontend hiding is UX only (Q33, Q38).

## 5. Non-functional requirements

- Local run: Next.js `:3000` → Spring `:8080` → Postgres `:5432` via docker compose (brief §8).
- Migrations: Flyway SQL, versioned, in repo. No H2 in tests — Testcontainers Postgres (step 7).
- API: REST, no `/v1` prefix (deliberate), paged envelope, sort whitelist, RFC 9457 errors (Q34, step 5).
- Testing: every domain rule has ≥1 test; no coverage-percentage chasing (step 7).
- Docs: this tree + ADRs for hard-to-reverse calls; decisions traceable to Q-numbers.
