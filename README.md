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

## Quick start (target, Phase 0)

```sh
docker compose up -d      # postgres
./mvnw spring-boot:run    # :8080
npm run dev --workspace web  # :3000
```

Seeded admin comes from env vars (`ADMIN_EMAIL` / `ADMIN_PASSWORD`). See `docs/development/roadmap.md` Phase 0 for the walking-slice definition.

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
