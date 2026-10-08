# web

Next.js (App Router) + TypeScript + shadcn/ui frontend for the Business Management System.

Run from the repo root:

```sh
npm install                 # npm workspaces; installs web/ too
npm run dev --workspace web # dev server on :3000
npm run test --workspace web # Vitest + Testing Library + MSW (CI-able, no watch)
npm run lint --workspace web
```

Layout follows `docs/frontend/frontend-architecture.md`; tests follow
`docs/testing/testing-strategy.md` §4 (no snapshots, no shadcn internals).

Add shadcn components with `npx shadcn add <name> -c web` (they land in `components/ui/`).
