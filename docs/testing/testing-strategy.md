# Testing strategy

Done = **every domain rule has ≥1 test**, not a coverage %. Testcontainers PostgreSQL everywhere integration is involved — never H2 (constraints and locking must be real).

## 1. Domain unit tests (pure, no mocks)

JUnit 5 + AssertJ. Every order transition legal + illegal; cancel-vs-return split; availability formula; weighted-average incl. stock-0; balance math (unpaid / partial / overpaid); overdue computation.

## 2. Slices (`@WebMvcTest`)

DTO shape → 400 + `fieldErrors`; domain exceptions → problem+json shape + status (409/422 split); 401/403 paths.

## 3. Integration (Testcontainers)

- Migrations run for real; CHECK/unique constraints bite.
- **Showcase**: confirm fails midway → full rollback; PROCESSING-entry atomicity (reservation released + SALE written, no double-count window).
- Concurrent confirm race: one wins, one gets 409 (row lock).
- Stock column == SUM(movements) reconciliation.
- Invoice issued exactly once; VOID excluded from balances; return's three money branches.
- **Permission-matrix test**: iterates Q33's table (role × action → allow/deny).
- Last-admin guard; deactivated-product-blocks-confirm.

## 4. Frontend (Vitest + Testing Library + MSW)

`fieldErrors` → inline; role-hidden buttons; required-reason dialog; filter state in URL; `initialData` (no double fetch). No shadcn-internals or snapshot-farming tests.

## 5. E2E (Playwright vs `docker compose`)

Golden path verbatim: login → create customer → create product → create order → confirm → inventory decreases → financial record created → dashboard reflects. Plus: return's three branches; EMPLOYEE blocked from `/finance`; CSV downloads. Untested: CSS, third-party internals.
