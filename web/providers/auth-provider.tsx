"use client";

import { createContext, useContext, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { apiBaseUrl } from "@/lib/api/base-url";

/** The signed-in User (GLOSSARY) as returned by `GET /api/auth/me`. */
export type AuthUser = {
  email: string;
  role: "ADMIN" | "MANAGER" | "EMPLOYEE";
};

const AUTH_ME_KEY = ["auth", "me"] as const;

async function fetchAuthMe(): Promise<AuthUser | null> {
  const response = await fetch(`${apiBaseUrl()}/api/auth/me`, {
    credentials: "include",
  });
  // Not-signed-in is an answer, not a failure: no crash, no stale user.
  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`GET /api/auth/me -> ${response.status}`);
  }
  return (await response.json()) as AuthUser;
}

type AuthContextValue = {
  /** `null` while loading and when the cookie is missing or expired. */
  user: AuthUser | null;
  /** True until the first `/auth/me` answer lands (no "Not signed in" flash). */
  isLoading: boolean;
  /** Re-read `/auth/me` — called after login sets the cookie. */
  refresh: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue>({
  user: null,
  isLoading: false,
  refresh: async () => {},
});

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: AUTH_ME_KEY,
    queryFn: fetchAuthMe,
  });

  const value: AuthContextValue = {
    user: data ?? null,
    isLoading,
    refresh: async () => {
      await queryClient.invalidateQueries({ queryKey: AUTH_ME_KEY });
    },
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  return useContext(AuthContext);
}
