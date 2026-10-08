"use client";

import { createContext, useContext, type ReactNode } from "react";

/** The signed-in User (GLOSSARY) as returned by `GET /api/auth/me`. */
export type AuthUser = {
  email: string;
  role: "ADMIN" | "MANAGER" | "EMPLOYEE";
};

type AuthContextValue = {
  /** `null` until `/auth/me` resolves. Slice 3 fills this in. */
  user: AuthUser | null;
};

const AuthContext = createContext<AuthContextValue>({ user: null });

export function AuthProvider({ children }: { children: ReactNode }) {
  // Stub: no fetch yet, so the app always renders signed-out.
  return (
    <AuthContext.Provider value={{ user: null }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  return useContext(AuthContext);
}
