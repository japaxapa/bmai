import { setupServer } from "msw/node";

/**
 * MSW server for component tests. Handlers are registered per test file via
 * `server.use(...)`; this module holds only the baseline (empty) set.
 */
export const server = setupServer();
