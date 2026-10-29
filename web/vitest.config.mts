import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

// Paths neither unit tests nor coverage should ever see: Playwright owns
// e2e/ (see test.exclude below). Shared so the two lists can't drift apart.
const sharedExcludes = ["e2e/**", "node_modules/**"];

export default defineConfig({
  plugins: [react()],
  resolve: {
    tsconfigPaths: true,
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./test/setup.ts"],
    restoreMocks: true,
    unstubEnvs: true,
    // e2e/ is Playwright's (test:e2e) — vitest must not try to run it in
    // jsdom, and playwright must not try to run vitest's tests.
    exclude: [...sharedExcludes],
    // Coverage: @vitest/coverage-v8 (issue #60). Note: coverage.exclude
    // REPLACES vitest's default exclude list, so it must restate everything
    // it cares about (hence the spread above) — plus tests, config files and
    // static assets that never go through the unit suite.
    //
    // Thresholds = ratchet floors, not targets (testing-strategy.md): under
    // them the run fails. Baselines were 92.80 lines / 86.18 branches — floors
    // sit ~3-4pt under and only ever move UP. Lines and branches get distinct
    // numbers: branch % is structurally lower (one untested `else` dents it
    // far more than a line).
    coverage: {
      provider: "v8",
      reporter: ["text", "html", "lcov"],
      thresholds: {
        lines: 90,
        branches: 82,
      },
      exclude: [
        ...sharedExcludes,
        "test/**",
        "**/__tests__/**",
        "**/*.d.ts",
        "next.config.ts",
        "playwright.config.ts",
        "vitest.config.mts",
        "proxy.ts",
        "public/**",
      ],
    },
  },
});
