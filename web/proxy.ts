import { jwtVerify } from "jose";
import { NextResponse, type NextRequest } from "next/server";
import { jwtSecret } from "@/lib/auth/jwt-secret";
import { ROLE_GATES, type Role } from "@/lib/role-gates";

/** The login cookie the backend sets (ADR 0015 / AuthCookie). */
const SESSION_COOKIE = "access_token";

/**
 * The session's role, or null when there is none to trust: no cookie, a
 * forged/garbage token, a wrong signing key, or an expired one.
 */
async function sessionRole(request: NextRequest): Promise<Role | null> {
  const token = request.cookies.get(SESSION_COOKIE)?.value;
  if (!token) {
    return null;
  }
  try {
    const { payload } = await jwtVerify(token, new TextEncoder().encode(jwtSecret()), {
      algorithms: ["HS256"],
    });
    // Backend-minted tokens always carry a Role (ADR 0015); anything else
    // fails the sessionRole contract and is treated as no session.
    return typeof payload.role === "string" ? (payload.role as Role) : null;
  } catch {
    return null;
  }
}

/**
 * Where a request goes before any page renders (Next 16 renamed middleware
 * to proxy): no valid session → `/login?next=…`; a session that is too
 * low-ranked for a gated path → `/` (it is signed in, so `/login` would be
 * a lie). The backend enforces the same rules independently.
 */
export async function proxy(request: NextRequest): Promise<NextResponse | undefined> {
  const { pathname } = request.nextUrl;

  // Returning nothing lets the request continue; only redirects are returned.

  // The login page must stay reachable, redirects included.
  if (pathname === "/login") {
    return;
  }

  const role = await sessionRole(request);
  if (role === null) {
    // Includes a correctly signed token that somehow lacks its role claim:
    // with no role to gate by, there is no session to trust (fail closed).
    // Carry the full original URL so login returns where the user was going.
    const returnTo = `${request.nextUrl.pathname}${request.nextUrl.search}`;
    return NextResponse.redirect(
      new URL(`/login?next=${encodeURIComponent(returnTo)}`, request.url),
    );
  }

  const gate = ROLE_GATES.find(
    ({ prefix }) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  );
  if (gate && !gate.roles.includes(role)) {
    return NextResponse.redirect(new URL("/", request.url));
  }
}

/** Skip static assets — auth there would only break page loading. */
export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
