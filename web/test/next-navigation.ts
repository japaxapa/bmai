import { vi } from "vitest";

/**
 * Test double for `next/navigation`.
 *
 * jsdom never mounts the app router, so any component that calls
 * `useRouter()` / `useSearchParams()` blows up with "invariant expected app
 * router to be mounted". Test files install it with:
 *
 *   vi.mock("next/navigation", () => import("@/test/next-navigation"));
 *
 * `push` records where the component wanted to go — the one thing a test can
 * observe about a redirect that jsdom will not perform for real.
 */
export const push = vi.fn();

export function useRouter() {
  return { push };
}

export function useSearchParams() {
  return new URLSearchParams(window.location.search);
}
