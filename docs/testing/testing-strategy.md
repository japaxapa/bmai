# Testing strategy

Done = **every domain rule has ≥1 test**, not a coverage %. Testcontainers PostgreSQL everywhere integration is involved — never H2 (constraints and locking must be real).

**Coverage is a ratchet floor, not a target** (issue #60): `verify`/`test:coverage` fail below the configured floors — frontend 90% lines / 82% branches (Vitest), backend 95% lines / 83% branches (JaCoCo). They only ever move **up**, and green floors never make a change "done" without the domain-rule test above. Lines and branches carry different numbers because branch % runs structurally lower.

## 1. Domain unit tests (pure, no mocks)

JUnit 5 + AssertJ. Every order transition legal + illegal; cancel-vs-return split; availability formula; weighted-average incl. stock-0; balance math (unpaid / partial / overpaid); overdue computation.

## 2. Slices (`@WebMvcTest`)

DTO shape → 400 + `fieldErrors`; domain exceptions → problem+json shape + status (409/422 split); 403 paths (401 and the DB-backed 409 are delegated — see Cell delegation below).

**Cell delegation** — where a cell lives when this seam can't raise it (say so in the slice's javadoc when delegating, so an audit of the checklist above doesn't read as a gap):

- **409 backed by a DB constraint** → §3, where the constraint is real. The slice's stubbed repository has no unique index, so the refusal is only pinnable where Postgres itself refuses the row — for a functional `lower()` index, both spellings must collide. Business-rule 409s the *service* raises (row-lock race) stay in §3 too.
- **401** → once repo-wide (`AuthEndpointTest`), not per slice: the session gate is the filter chain and doesn't vary per endpoint. Per-slice, only the **403** cells vary — each `@PreAuthorize` is its own role × action cell (§3's matrix).

## 3. Integration (Testcontainers)

- Migrations run for real; CHECK/unique constraints bite.
- **Showcase**: confirm fails midway → full rollback; PROCESSING-entry atomicity (reservation released + SALE written, no double-count window).
- Concurrent confirm race: one wins, one gets 409 (row lock).
- Stock column == SUM(movements) reconciliation.
- Invoice issued exactly once; VOID excluded from balances; return's three money branches.
- **Permission-matrix test**: iterates Q33's table (role × action → allow/deny).
- Last-admin guard; deactivated-product-blocks-confirm.

## 4. Frontend (Vitest + Testing Library + MSW)

`fieldErrors` → inline; refusal detail (409/422) → inline; role-hidden buttons; required-reason dialog; filter state in URL; seed hydration (no double fetch, ADR 0020). No shadcn-internals or snapshot-farming tests.

## 5. E2E (Playwright vs `docker compose`)

Golden path verbatim: login → create customer → create product → create order → confirm → inventory decreases → financial record created → dashboard reflects. Plus: return's three branches; EMPLOYEE blocked from `/finance`; CSV downloads. Untested: CSS, third-party internals.
