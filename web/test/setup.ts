import { afterAll, afterEach, beforeAll } from "vitest";
import { cleanup } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { server } from "./server";

// jsdom doesn't implement matchMedia; sonner (toasts) reads it on mount.
// A neutral "no preference / doesn't match" answer suits every component here.
// The proxy suite runs in a node environment, so `window` may not exist.
if (typeof window !== "undefined" && !window.matchMedia) {
  window.matchMedia = (query: string): MediaQueryList =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList;
}

// Vitest runs without globals, so Testing Library's auto-cleanup never hooks in.
afterEach(() => cleanup());

beforeAll(() => server.listen({ onUnhandledRequest: "bypass" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
