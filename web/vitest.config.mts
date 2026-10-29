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
    coverage: {
      provider: "v8",
      reporter: ["text", "html", "lcov"],
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
