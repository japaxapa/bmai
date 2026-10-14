import { cookies } from "next/headers";
import { SESSION_COOKIE } from "@/lib/auth/session-cookie";

/**
 * The one server-side credential helper (`architecture.md` §4's "one
 * `lib/api` helper"): what a server-component fetch of `:8080` sends so the
 * backend sees the same session the browser would (ADR 0020).
 *
 * Forwards the cookie verbatim — decodes nothing, no `/auth/me`, and no
 * error when it's absent: a request without a cookie simply goes out
 * unauthenticated, and the caller (the table seed) degrades like it does
 * for every other failure.
 */
export async function sessionInit(): Promise<RequestInit> {
  const token = (await cookies()).get(SESSION_COOKIE)?.value;
  if (!token) {
    return {};
  }
  return { headers: { Cookie: `${SESSION_COOKIE}=${token}` } };
}
