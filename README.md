# Business Management System

Portfolio project: B2B wholesale distributor management system.

- **Backend:** Java + Spring Boot (REST)
- **Frontend:** Next.js + TypeScript + shadcn/ui (App Router)
- **Database:** PostgreSQL + Flyway
- **Auth:** Stateless JWT in httpOnly cookie, role-based (`ADMIN`, `MANAGER`, `EMPLOYEE`)
- **Environment:** Local-first (`docker compose`), seed data included. Free-tier deploy documented as optional.

## What this system is

A fictional Brazilian B2B wholesaler (BRL, CPF/CNPJ-style documents; code/docs/UI in English) that buys products from suppliers and resells to business customers on **credit terms (net-30 default)**.

Core flow:

```text
Supplier → Purchase Order → Inventory (movement ledger) → Sales Order → Invoice → Payment → Dashboard
```

Depth is concentrated on **orders, inventory, and finance**. All other modules are honest and shallow. See `docs/development/roadmap.md` for the phased plan and `docs/domain/business-rules.md` for the rules that make this more than CRUD.

## Quick start

```sh
docker compose up -d      # postgres (healthcheck; data persists in the db-data volume)
./mvnw spring-boot:run    # backend on :8080 — verify with GET /actuator/health
./mvnw test               # integration tests against Testcontainers PostgreSQL (never H2)
```

Backend environment overrides: `DB_URL` / `DB_USER` / `DB_PASSWORD` (defaults match compose), and `SERVER_PORT` when 8080 is taken. Flyway migrates automatically on every boot; re-boots are a no-op.

Frontend (`npm run dev --workspace web` → :3000) and the seeded admin (`ADMIN_EMAIL` / `ADMIN_PASSWORD`) arrive with the auth and UI phases. See `docs/development/roadmap.md` for the phased plan.

## Docs

```text
GLOSSARY.md                        # canonical domain language
docs/
├── requirements/requirements.md
├── architecture/architecture.md
├── adr/                           # ~15 decision records
├── domain/domain-model.md
├── domain/business-rules.md
├── database/database-design.md
├── api/api-design.md
├── frontend/frontend-architecture.md
├── testing/testing-strategy.md
└── development/roadmap.md
```

Start with `docs/requirements/requirements.md`, then `docs/domain/domain-model.md` + `docs/domain/business-rules.md`.

## Design highlights (interview stories)

- Inventory is an **append-only movement ledger**, not a stock column (ADR-006).
- Orders **reserve at CONFIRMED, decrement at PROCESSING**; order status is **independent of payment status** (ADR-007, ADR-008).
- Invoices issue **once, at COMPLETED**; cancel ≠ return (ADR-009, ADR-010).
- Cost is a **weighted average + per-item snapshot**; profit = gross margin − opex (ADR-011).
- Money moves are **cash-only transactions**; corrections are **VOID + re-enter**, never edits (ADR-012, ADR-013).
- Backend enforces permissions with **`@PreAuthorize`**; frontend hiding is UX only.
- Errors are **RFC 9457 problem+json** with a 400 / 409 / 422 split.
