"use client";

import { createContext, useContext, type ReactNode } from "react";

export type Theme = "light" | "dark" | "system";

// The read-only half of the theme on purpose: there is no setter until a
// ticket actually specces the theme feature (slice 0 allowed a stub provider).
// A `setTheme` here would type-check and silently do nothing — better that a
// future toggle fails to compile against the gap than debugs a noop.
type ThemeContextValue = {
  theme: Theme;
};

const ThemeContext = createContext<ThemeContextValue>({
  theme: "system",
});

export function ThemeProvider({ children }: { children: ReactNode }) {
  return (
    <ThemeContext.Provider value={{ theme: "system" }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme(): ThemeContextValue {
  return useContext(ThemeContext);
}
