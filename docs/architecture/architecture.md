# Architecture

## 1. Backend — package by feature (Q29)

```text
com.example.bms/
├── order/        # controller/, service/, repository/, entity/, dto/
├── inventory/
├── finance/
├── product/      # includes categories
├── customer/
├── supplier/
├── purchase/
├── auth/         # users, login, JWT
└── shared/       # ProblemDetail advice, security config, pagination
```

- Rule: features interact only through another feature's **public service**, never through each other's repositories.
- Monolith on purpose. Microservices, CQRS, event sourcing explicitly rejected for this scale.
- Stretch goal: Spring Modulith / ArchUnit test failing the build on repository cross-access.

## 2. Domain placement — rich objects (Q30)

- Invariants live on entities: `order.confirm()`, `product.checkAvailable()`, `order.cancel()`.
- Application service = transaction + load + authorize + save.
- Validation seam: DTO shape → Bean Validation `@Valid`; business rules → domain exceptions → problem+json (Q34).

## 3. Orchestration — one transactional method (Q31)

- `confirmOrder`, `startProcessing`, `completeOrder`, `receivePurchase`, `returnOrder`, `recordPayment` are each **one `@Transactional` method**.
- `@Transactional` on application services only; `readOnly = true` on queries; rollback on runtime exceptions.
- Domain events rejected for v1 (chasing listeners to learn what confirm does is worse than one readable method). Cut-list with a note on when to switch (module doubling, webhooks, async fan-out).

## 4. Auth — JWT in httpOnly cookie (Q32)

- Claims: `sub`, `role`, `exp`. BCrypt(10). ~12h token, logout = clear cookie. No refresh tokens (a token store is state).
- CORS: exactly `http://localhost:3000` with credentials. CSRF disabled with documented rationale (JSON-only API + SameSite=Lax cookie).
- Server components forward the cookie via one `lib/api` helper when calling `:8080`.

## 5. Authorization — method security (Q33)

- Base URL layer: everything authenticated except the endpoints that must answer without a credential: login, logout (a token that has gone bad must still be clearable), health and `/error`.
- Write rules: `@PreAuthorize` on service methods (`@EnableMethodSecurity`). URL regex farms rejected (miss-a-line = open endpoint); domain-coupled role checks rejected.
- Full matrix in `docs/domain/business-rules.md`. One integration test iterates role × action → allow/deny.

## 6. Errors — RFC 9457 (Q34)

- `ProblemDetail` + one `@RestControllerAdvice`. Domain throws `BusinessException` hierarchy.
- 400 shape · 401/403 · 404 · **409 state/stock conflicts** · **422 business-rule refusals** · 500 without stack leaks.
- *"400 = malformed; 422 = understood but the business refuses."*

## 7. Frontend — feature-first (Q35)

```text
app/
  (auth)/login/page.tsx
  (dashboard)/{customers,products,suppliers,orders,inventory,purchases,finance,reports,users,categories}/...
    page.tsx + loading.tsx + error.tsx + _components/   # route-only pieces
features/<f>/components|hooks|api.ts                     # shared within a feature
components/ui/  components/shared/                       # shadcn + DataTable, PageHeader, …
providers/      # auth, query, theme — exactly 3
hooks/ lib/ types/                                       # generic hooks, api client, DTO mirrors
```

- Thin pages; `services/` dissolved into `features/<f>/api.ts`; one shared `DataTable`.

## 8. Data fetching — hybrid, one owner per datum (Q36)

- Dashboard = server component fetching `:8080` directly. Tables = client islands seeded with `initialData` (no double fetch). Filters in URL search params. Mutations → React Query invalidation. Direct `:8080` calls (no BFF proxy). `useEffect`-fetching banned.

## 9. State, routes, forms (Q37–Q39)

- Providers: auth (fed from `GET /auth/me` over the httpOnly cookie — a browser-side read; ADR 0018 records why not the spec'd server-read), query client, theme. Nothing else is global.
- Middleware (`jose` verify): unauthenticated → `/login?next=…`; role-mismatched groups redirect; UI hides by role (UX only).
- Forms: react-hook-form + zod (shape courtesy only); 409/422 `fieldErrors` → inline; ConfirmDialog for destructive actions (reason required for adjustments); `loading.tsx`/`error.tsx`; no optimistic updates v1; 401 interceptor → login.
