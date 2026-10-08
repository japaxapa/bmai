"use client";

import { createContext, useContext, type ReactNode } from "react";

export type Theme = "light" | "dark" | "system";

type ThemeContextValue = {
  theme: Theme;
  setTheme: (theme: Theme) => void;
};

const noop = () => {
  // Stub: persistence arrives with the theme feature.
};

const ThemeContext = createContext<ThemeContextValue>({
  theme: "system",
  setTheme: noop,
});

export function ThemeProvider({ children }: { children: ReactNode }) {
  return (
    <ThemeContext.Provider value={{ theme: "system", setTheme: noop }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme(): ThemeContextValue {
  return useContext(ThemeContext);
}
