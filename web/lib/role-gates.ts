/**
 * GLOSSARY Role: a closed set, so every gate below is checked by tsc.
 * The one write-down of the role union in the frontend.
 */
export type Role = "ADMIN" | "MANAGER" | "EMPLOYEE";

/**
 * Which roles may enter which area — the single source of that business
 * rule in the frontend. Two layers read it, per frontend-architecture
 * §State: the middleware (proxy.ts) *enforces* it, the dashboard nav
 * (shell.tsx) merely *hides* by it (UX only — the backend re-checks
 * every request independently).
 *
 * Dependency-free on purpose: proxy.ts runs outside the React tree, so
 * nothing here may import React, providers, or node builtins.
 */
export const ROLE_GATES: readonly {
  prefix: string;
  roles: readonly Role[];
}[] = [
  { prefix: "/finance", roles: ["ADMIN", "MANAGER"] },
  { prefix: "/users", roles: ["ADMIN"] },
  { prefix: "/categories", roles: ["ADMIN"] },
];

/**
 * The roles allowed at an area prefix, or `undefined` when the area is
 * unlisted — meaning any signed-in role may enter.
 */
export function rolesFor(prefix: string): readonly Role[] | undefined {
  return ROLE_GATES.find((gate) => gate.prefix === prefix)?.roles;
}
