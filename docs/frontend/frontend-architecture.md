# Frontend architecture

Next.js App Router + TypeScript + shadcn/ui. Feature-first (Q35), hybrid fetching (Q36), 3 providers (Q37), 3-layer protection (Q38), governed forms/errors (Q39).

## Structure

```text
app/
  (auth)/login/page.tsx
  (dashboard)/
    layout.tsx                     # sidebar + header shell
    page.tsx                       # dashboard (server component)
    customers/page.tsx  [id]/page.tsx  + loading.tsx + error.tsx + _components/
    products/  suppliers/  orders/ (new, [id], [id]/edit)  inventory/  purchases/
    finance/ (invoices, payables, transactions)  reports/  users/  categories/
features/<f>/components|hooks|api.ts
components/ui/  components/shared/ # DataTable (shadcn + tanstack), PageHeader, EmptyState, ConfirmDialog
providers/  hooks/  lib/  types/
```

One-home rule: route-specific → colocate; feature-shared → `features/<f>/`; app-wide → top-level. Pages stay thin. `services/` dissolved into `features/<f>/api.ts`.

## Data fetching

- Dashboard: server component → `:8080` via `lib/api` (cookie forwarded). Skeletons via `loading.tsx`.
- Tables: client islands seeded with server `initialData`; filters in URL search params (shareable, back-button-safe); React Query mutations → invalidation.
- Direct `:8080` calls, `credentials: "include"`; no BFF proxy. `useEffect`-fetching banned.

## State, auth UX, forms

- Providers: auth (browser reads the httpOnly cookie via `GET /auth/me` → `{email, role}` into client provider; ADR 0018 records why not the spec'd server-read), query client, theme. Nothing else global.
- Middleware (`jose`): unauthenticated → `/login?next=…`; `/finance/**` → ADMIN|MANAGER; `/users`, `/categories` → ADMIN. UI hides by role (UX only); backend enforces.
- Login: form → `POST :8080/api/auth/login` → cookie set by API → redirect `next`. Logout clears.
- Forms: react-hook-form + zod (shape courtesy only). 409/422 `fieldErrors` → inline; else toast. Destructive actions → shared `ConfirmDialog` (reason required for adjustments). No optimistic updates v1. 401 interceptor → login with `next`.
