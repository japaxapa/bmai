import { defineConfig, devices } from "@playwright/test";
import { E2E_API_PORT } from "./e2e/support/api-base";

/**
 * E2E harness (issue #45, testing-strategy §5): the real stack end to end —
 * compose Postgres, the Spring backend, and a *production* `next build` —
 * because the subject under test is server-seeded first paint, which only
 * the production renderer (`cacheComponents` streaming, ADR 0020) delivers.
 *
 * One command from the repo root or web/: `npm run test:e2e`. The first run
 * also needs `npx playwright install chromium` (once per machine).
 *
 * `reuseExistingServer: false` is deliberate on both servers: a dev server
 * left on :3000 would silently become the subject under test, and the seed
 * AC would be "proven" against a renderer it doesn't describe. When the port
 * is taken, fail loudly instead.
 */
export default defineConfig({
  testDir: "./e2e",
  // One shared database and one backend: specs create rows and assert on
  // pagination, so they take turns rather than racing.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["list"]] : [["list"]],
  outputDir: "test-results",
  use: {
    baseURL: "http://localhost:3000",
    // Actionable failures (ticket #22's AC, seeded here): the first retry
    // carries a trace, a failure carries the page.
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: [
    {
      // The script brings compose up (waiting for the healthcheck), drops
      // and recreates the throwaway `bms_e2e` database — so every run starts
      // from freshly migrated schema with zero rows, and the dev `bms` data
      // volume is never touched — then boots Spring pointed at it. Tomcat
      // only binds after context refresh (Flyway included), so a responding
      // backend is a migrated one.
      //
      // Port E2E_API_PORT (8082) — distinct from both the dev default (:8081)
      // and the contested :8080. An unrelated process squatting a port answers
      // the readiness GET with silence, and Playwright's probe has no timeout:
      // the run would hang forever before printing anything.
      command: "node e2e/start-backend.mjs",
      url: `http://localhost:${E2E_API_PORT}/actuator/health`,
      env: { SERVER_PORT: String(E2E_API_PORT) },
      reuseExistingServer: false,
      timeout: 300_000,
    },
    {
      // `next build` (the PPR report is part of the evidence) + start. The
      // build also fails the run on prerender errors the seed could cause.
      // NEXT_PUBLIC_API_BASE_URL is inlined at build time (base-url.ts), so
      // the browser bundle and the server-side seed both target E2E_API_PORT.
      command: "npm run build && npm run start",
      url: "http://localhost:3000",
      env: { NEXT_PUBLIC_API_BASE_URL: `http://localhost:${E2E_API_PORT}` },
      reuseExistingServer: false,
      timeout: 300_000,
    },
  ],
});
