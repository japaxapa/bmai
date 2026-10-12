import { useSyncExternalStore } from "react";
import { vi } from "vitest";

/**
 * Test double for `next/navigation`.
 *
 * jsdom never mounts the app router, so any component that calls
 * `useRouter()` / `useSearchParams()` / `usePathname()` blows up with
 * "invariant expected app router to be mounted". Test files install it with:
 *
 *   vi.mock("next/navigation", () => import("@/test/next-navigation"));
 *
 * Navigation is modelled the way the app router behaves for search-param
 * updates: `push`/`replace` move jsdom's own URL (recording where the
 * component wanted to go — the one thing a test can observe about a
 * redirect jsdom will not perform for real) and notify subscribers, so a
 * component reading `useSearchParams()` re-renders with the new params.
 * That is what makes a "click Next → next page fetched" integration test
 * possible without a browser.
 */

const listeners = new Set<() => void>();

function navigate(href: string) {
  window.history.replaceState({}, "", href);
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

// getSnapshot must return a cached reference until the URL actually changes
// (React re-renders forever otherwise) — one URLSearchParams per query string.
let lastSearch: string | null = null;
let lastParams = new URLSearchParams();

function getSnapshot() {
  const search = window.location.search;
  if (search !== lastSearch) {
    lastSearch = search;
    lastParams = new URLSearchParams(search);
  }
  return lastParams;
}

export const push = vi.fn((href: string) => navigate(href));
export const replace = vi.fn((href: string) => navigate(href));

export function useRouter() {
  return { push, replace };
}

export function usePathname() {
  return window.location.pathname;
}

export function useSearchParams() {
  return useSyncExternalStore(subscribe, getSnapshot);
}
