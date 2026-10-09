/** The documented default (README quick start): the API on :8080. */
export const DEFAULT_API_BASE_URL = "http://localhost:8080";

/**
 * Where the API lives, per call — so a test or a run can point it elsewhere.
 *
 * - `API_BASE_URL` is server-side only: Next compiles references to it in
 *   client bundles down to an empty string, which is why this reads with `||`.
 * - `NEXT_PUBLIC_API_BASE_URL` is inlined into the browser bundle at build
 *   time, and is the one to set when this box's 8080 is already taken.
 */
export function apiBaseUrl(): string {
  return (
    process.env.API_BASE_URL ||
    process.env.NEXT_PUBLIC_API_BASE_URL ||
    DEFAULT_API_BASE_URL
  );
}
