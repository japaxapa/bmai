/** The documented default (README quick start): the API on :8080. */
export const DEFAULT_API_BASE_URL = "http://localhost:8080";

/**
 * Where the API lives, per call — so a test or a run can point it elsewhere.
 *
 * Only `NEXT_PUBLIC_API_BASE_URL` is honoured: it is the single knob for
 * both graphs. Next inlines `NEXT_PUBLIC_*` into the browser bundle at
 * build time; the same name reads from the process env on the server, where
 * the seeded table fetch (ADR 0020) resolves it at request time. No new env
 * var — a bare `API_BASE_URL` would compile down to an empty string
 * client-side, so it is deliberately not read here.
 */
export function apiBaseUrl(): string {
  return process.env.NEXT_PUBLIC_API_BASE_URL || DEFAULT_API_BASE_URL;
}
