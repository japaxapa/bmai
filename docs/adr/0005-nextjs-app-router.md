# Use Next.js App Router

App Router with the hybrid fetching policy (server dashboard, client table islands with `initialData`) demonstrates the actual Next.js skill — server/client boundaries, cookie forwarding, URL-kept filters. A pure SPA would abandon the brief's Server Component goals; full-server would give up interactive-table craft.

Amended after the two-axis review of #44: the table islands are seeded with server-dehydrated state (`dehydrate` + `HydrationBoundary`), not React Query `initialData` — ADR 0020 records why the mechanism changed. The hybrid server/client policy itself stands.
