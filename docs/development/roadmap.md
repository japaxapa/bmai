# Roadmap

Strategy (b): all modules honest-shallow, depth on orders/inventory/finance. Fallback to (c) via the cut-list. Each phase DoD: tests green, migration clean, `business-rules.md` + `api-design.md` updated, screenshot if UI-visible.

## Phase 0 — Walking slice

Repo layout, compose (db), Flyway, Security+JWT, Next+shadcn+3 providers, middleware, login → `/`. **Done:** seeded admin logs in through the UI (screenshot in `assets/`); `/auth/me` returns role; route protection verified — unauthenticated → `/login?next=…`, session survives refresh.

## Phase 1 — Catalog

Categories, products, suppliers CRUD + status + constraints. **Done:** unique violations surface as 422/409 through the UI.

## Phase 2 — Orders + Inventory ⭐

Full order lifecycle, reservations + row lock, movements, adjustments, inventory views. **Done:** rollback / race / state-machine / matrix tests green.

## Phase 3 — Purchases

PO lifecycle, atomic receive (movement + weighted avg + payable), supplier payments. **Done:** avg formula verified incl. stock 0.

## Phase 4 — Money

Payments-in, void, return → credit note → refund, opex, transaction history. **Done:** three return branches pass; VOID excluded from balances.

## Phase 5 — Dashboard + Reports

Single endpoint, charts (recharts), CSV exports. **Done:** one fetch paints the dashboard; CSV downloads.

## Phase 6 — Polish + docs

Error/empty/loading states, README screenshots/GIF, E2E suite, docs finalized. **Done:** fresh clone → `compose up` → demo path works.

## Phase 7 — Optional

Free-tier deploy recipe (Vercel + Neon + Render-free), CI. Documented effort only — never blocking; screenshots already cover the portfolio.

## Cut-list (explicit backlog)

Partial receipts · partial returns · real supplier-return flow · multi-location · reservation expiry · per-order terms · dunning · password-reset email · tax/NF-e · FIFO layers · richer reports · refresh tokens · domain events · Modulith enforcement · optimistic UI · rate limiting.
