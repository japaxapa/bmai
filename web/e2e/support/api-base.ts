/**
 * Where the e2e backend lives (issue #45's harness, testing-strategy §5).
 *
 * Deliberately distinct from the dev default (:8081): dev and e2e backends
 * can then run side by side, and nothing here collides with whatever the dev
 * box has squatting :8080 (here: MiniTool ShadowMaker's `MTAgentService`,
 * which accepts TCP and never answers HTTP). Playwright's webServer readiness
 * GET has no socket timeout — an unanswered probe hangs the run forever
 * without printing a single line, which is why the suite stays off the
 * contested ports entirely.
 *
 * One constant, read by `playwright.config.ts` (health URL, `SERVER_PORT`,
 * `NEXT_PUBLIC_API_BASE_URL` — inlined into the build) and re-exported by
 * `fixtures.ts` for specs.
 */
export const E2E_API_PORT = 8082;

/** The backend the web app talks to directly (no BFF — frontend-architecture). */
export const API_BASE_URL = `http://localhost:${E2E_API_PORT}`;
