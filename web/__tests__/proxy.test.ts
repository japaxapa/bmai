// The proxy is server-side Node code (Next 16 runs it on the Node runtime),
// so it is tested outside jsdom: jose's key checks and fs reads need real
// Node globals.
// @vitest-environment node
import { readFileSync } from "node:fs";
import { NextRequest } from "next/server";
import { SignJWT } from "jose";
import { describe, expect, test, vi } from "vitest";
import { DEV_JWT_SECRET } from "@/lib/auth/jwt-secret";
import { proxy } from "@/proxy";

const BASE = "http://localhost:3000";

/** The proxy's view of a browser hit: this path, optionally with the login cookie. */
function requestTo(path: string, cookie?: string) {
  return new NextRequest(`${BASE}${path}`, {
    headers: cookie ? { cookie: `access_token=${cookie}` } : {},
  });
}

/** A cookie the backend would have set: HS256 over `sub`/`role`/`exp` (ADR 0015). */
function signedCookie(role: string, secret = DEV_JWT_SECRET) {
  return new SignJWT({ role })
    .setProtectedHeader({ alg: "HS256" })
    .setSubject("admin@bms.local")
    .setExpirationTime("12h")
    .sign(new TextEncoder().encode(secret));
}

function redirectTo(response: Response | undefined) {
  expect(response, "expected a redirect").toBeDefined();
  return new URL(response!.headers.get("location")!);
}

/** The login bounce: where it lands, and the `next` it wants back afterwards. */
function expectLoginRedirect(response: Response | undefined) {
  const location = redirectTo(response);
  expect(location.pathname).toBe("/login");
  return new URLSearchParams(location.search).get("next");
}

describe("proxy: unauthenticated requests", () => {
  test("no cookie on / goes to /login?next=%2F", async () => {
    // The helper decodes the param, so `next=%2F` arrives as `/`.
    expect(expectLoginRedirect(await proxy(requestTo("/")))).toBe("/");
  });

  test("a garbage cookie is treated as no session", async () => {
    expect(expectLoginRedirect(await proxy(requestTo("/", "not-a-jwt")))).toBe("/");
  });

  test("the next param carries the original query string back from login", async () => {
    expect(expectLoginRedirect(await proxy(requestTo("/finance?tab=1")))).toBe(
      "/finance?tab=1",
    );
  });

  test("/login itself stays reachable without a cookie", async () => {
    expect(await proxy(requestTo("/login"))).toBeUndefined();
  });
});

describe("proxy: authenticated requests", () => {
  test("a valid cookie passes through", async () => {
    expect(await proxy(requestTo("/", await signedCookie("ADMIN")))).toBeUndefined();
  });

  test("a cookie signed with a rotated-out key is treated as no session", async () => {
    vi.stubEnv("AUTH_JWT_SECRET", "a-rotated-shared-key");

    expect(expectLoginRedirect(await proxy(requestTo("/", await signedCookie("ADMIN"))))).toBe(
      "/",
    );
  });
});

describe("proxy: role gates", () => {
  test("EMPLOYEE is kept out of /finance, ADMIN gets in", async () => {
    const employee = redirectTo(
      await proxy(requestTo("/finance", await signedCookie("EMPLOYEE"))),
    );
    const admin = await proxy(requestTo("/finance", await signedCookie("ADMIN")));

    expect(employee.pathname).toBe("/");
    expect(admin).toBeUndefined();
  });

  test("EMPLOYEE is kept out of /users", async () => {
    const location = redirectTo(
      await proxy(requestTo("/users", await signedCookie("EMPLOYEE"))),
    );

    expect(location.pathname).toBe("/");
  });
});

describe("proxy: shared secret parity (ADR 0015)", () => {
  test("the committed dev default matches application.properties", () => {
    const properties = readFileSync(
      new URL("../../src/main/resources/application.properties", import.meta.url),
      "utf8",
    );
    const committed = properties.match(
      /^auth\.jwt\.secret=\$\{AUTH_JWT_SECRET:([^}]+)\}$/m,
    )?.[1];

    expect(committed, "default not found in application.properties").toBeDefined();
    expect(DEV_JWT_SECRET).toBe(committed);
  });
});
