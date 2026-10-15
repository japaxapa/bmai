import { expect, type APIRequestContext } from "@playwright/test";

/**
 * The e2e fixture base (issue #45): the plumbing every future spec should
 * reuse instead of reinventing — sign-in, authenticated API calls, and
 * collision-proof names. Specs import `test`/`expect` from here, never from
 * `@playwright/test` directly, so the fixtures stay attachable.
 */

import { API_BASE_URL } from "./api-base";

export { API_BASE_URL, E2E_API_PORT } from "./api-base";

/** The seeded demo admin (README; migration V2) — ADMIN for the write paths. */
export const ADMIN = { email: "admin@bms.local", password: "admin123" };

/**
 * A name no previous run can have created: the DB is throwaway-per-run, but
 * within a run (and against a reused dev DB by mistake) uniqueness must hold
 * because the categories index compares lower(name) (V3).
 */
export function uniqueName(prefix: string): string {
  return `${prefix} ${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

/**
 * Sign in through the real /api/auth/login and leave the session cookie in
 * the request context's jar — pass `context.request` and the browser context
 * inherits it (`context.request` shares the cookie jar), the same httpOnly
 * cookie a UI login sets and the same one the Next proxy and the server seed
 * read (ADR 0015). A cookie set by the API is host-scoped, not port-scoped,
 * so the browser carries it to :3000 as in the real product.
 *
 * Specs that merely *need a session* (everything except the login flow
 * itself) call this; UI-login coverage belongs to the §5 golden path (#22).
 */
export async function login(
  request: APIRequestContext,
  credentials: { email: string; password: string } = ADMIN,
): Promise<void> {
  const response = await request.post(`${API_BASE_URL}/api/auth/login`, {
    data: credentials,
  });
  expect(response.ok(), `login as ${credentials.email} -> ${response.status()}`).toBe(true);
}

/**
 * An API call with the context's session attached (context.request shares
 * the cookie jar) — for seeding fixtures (`createCategory`) and for reading
 * API truth to compare the UI against. Throws on non-2xx with the problem
 * body, since every helper here sets up, never asserts failures.
 */
export async function api(
  request: APIRequestContext,
  method: "get" | "post" | "put",
  path: string,
  body?: unknown,
) {
  const response = await request.fetch(`${API_BASE_URL}${path}`, {
    method,
    ...(body === undefined
      ? {}
      : { headers: { "Content-Type": "application/json" }, data: body }),
  });
  expect(
    response.ok(),
    `${method.toUpperCase()} ${path} -> ${response.status()}: ${await response.text()}`,
  ).toBe(true);
  return response;
}
