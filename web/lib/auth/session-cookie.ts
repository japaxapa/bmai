/**
 * The login cookie the backend sets (ADR 0015 / AuthCookie) — one literal
 * for both edges that handle it: `proxy.ts` (which verifies it before any
 * page renders) and `lib/api/server.ts` (which forwards it to `:8081`).
 */
export const SESSION_COOKIE = "access_token";
