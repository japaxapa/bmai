import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

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
    exclude: ["e2e/**", "node_modules/**"],
    // Coverage: @vitest/coverage-v8 (issue #60). Excludes mirror what the
    // test runner itself skips — tests, e2e, config and generated files.
    coverage: {
      provider: "v8",
      reporter: ["text", "html", "lcov"],
      exclude: [
        "e2e/**",
        "node_modules/**",
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
