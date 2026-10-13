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
components/ui/  components/shared/ # DataTable (shadcn + tanstack), PageHeader, EmptyState, ConfirmDialog, PagedList
providers/  hooks/  lib/  types/
```

One-home rule: route-specific → colocate; feature-shared → `features/<f>/`; app-wide → top-level. Pages stay thin. `services/` dissolved into `features/<f>/api.ts`.

## Data fetching

- Dashboard: server component → `:8080` via `lib/api` (cookie forwarded). Skeletons via `loading.tsx`.
- Tables: client islands seeded with server `initialData`; filters in URL search params (shareable; page/filter changes use `replace`, so back leaves the list rather than walking pages — ADR 0019); React Query mutations → invalidation.
- Direct `:8080` calls, `credentials: "include"`; no BFF proxy. `useEffect`-fetching banned.
- Pagination (ADR 0019): `usePageParam` (`hooks/`) holds the 0-based page in `?page=` — the same number the API takes — and `<PagedList>` (`components/shared/`) renders the loading/empty/error states, the "x–y of total" summary and the prev/next bounds around a list's rows — plus, for a URL past the last page, a 1-based "Page N of M" with a one-click "Go to last page" way back. Each list keeps its fetch: query key includes the page, prefix invalidation clears every cached page.

## State, auth UX, forms

- Providers: auth (browser reads the httpOnly cookie via `GET /auth/me` → `{email, role}` into client provider; ADR 0018 records why not the spec'd server-read), query client, theme. Nothing else global.
- Middleware (`jose`): unauthenticated → `/login?next=…`; `/finance/**` → ADMIN|MANAGER; `/users` → ADMIN. `/categories` is unlisted — any signed-in role enters (the list is vocabulary every role reads, business-rules §4); its write controls are ADMIN-only in the UI. UI hides by role (UX only); backend enforces.
- Login: form → `POST :8080/api/auth/login` → cookie set by API → redirect `next`. Logout clears.
- Forms: react-hook-form + zod (shape courtesy only). 409/422 `fieldErrors` → inline; else toast. Destructive actions → shared `ConfirmDialog` (reason required for adjustments). No optimistic updates v1. 401 interceptor → login with `next`.
