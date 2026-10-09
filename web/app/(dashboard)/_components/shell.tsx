"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useAuth, type AuthUser } from "@/providers/auth-provider";

type NavItem = {
  href: string;
  label: string;
  /** When set, only these roles see the item (GLOSSARY Role). */
  roles?: readonly AuthUser["role"][];
};

/** UI hides by role — UX only; the backend enforces (frontend-architecture §State). */
const NAV_ITEMS: readonly NavItem[] = [
  { href: "/", label: "Dashboard" },
  { href: "/users", label: "Users", roles: ["ADMIN"] },
];

function canSee(item: NavItem, role: AuthUser["role"] | null): boolean {
  if (!item.roles) {
    return true;
  }
  return role !== null && item.roles.includes(role);
}

/** The dashboard shell: sidebar nav + header reflecting `/auth/me` (issue #31). */
export function Shell({ children }: { children: ReactNode }) {
  const { user, isLoading } = useAuth();

  return (
    <div className="flex min-h-full">
      <aside className="flex w-56 flex-col gap-4 border-r border-neutral-200 p-4 dark:border-neutral-800">
        <p className="text-sm font-semibold">Business Management System</p>
        <nav className="flex flex-col gap-1">
          {NAV_ITEMS.filter((item) => canSee(item, user?.role ?? null)).map(
            (item) => (
              <Link
                key={item.href}
                href={item.href}
                className="rounded-md px-2 py-1.5 text-sm hover:bg-neutral-100 dark:hover:bg-neutral-900"
              >
                {item.label}
              </Link>
            ),
          )}
        </nav>
      </aside>
      <div className="flex flex-1 flex-col">
        <header className="flex items-center justify-end border-b border-neutral-200 px-6 py-3 text-sm dark:border-neutral-800">
          {!isLoading &&
            (user ? (
              <span className="flex items-center gap-2">
                <span>{user.email}</span>
                <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs font-medium dark:bg-neutral-900">
                  {user.role}
                </span>
              </span>
            ) : (
              <span>Not signed in</span>
            ))}
        </header>
        <main className="flex-1 p-6">{children}</main>
      </div>
    </div>
  );
}
