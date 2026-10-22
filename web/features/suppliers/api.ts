import { queryOptions } from "@tanstack/react-query";
import { apiBaseUrl } from "@/lib/api/base-url";
import type { PagedEnvelope } from "@/types/paged-envelope";

/**
 * The suppliers list's fetch and query key — the `features/<f>/api.ts` home
 * frontend-architecture §Structure mandates (same as categories and products:
 * the server seed calls these too, and a value export can't come from a
 * `"use client"` module).
 */

/** One row of the party (GLOSSARY Supplier). */
export type Supplier = {
  id: number;
  name: string;
  /** Canonical form (GLOSSARY): digits only — the punctuation is display. */
  document: string;
  email: string | null;
  paymentTermsDays: number;
  active: boolean;
};

/**
 * What the write DTO takes (create and edit share the whole shape).
 * `email` and `paymentTermsDays` are optional: contact info a party may not
 * have yet, and terms the database is born with at 30 (business-rules §3) —
 * an empty box stays *out* of the body, so the server's own default is the
 * one that applies.
 */
export type SupplierBody = {
  name: string;
  document: string;
  email?: string | null;
  paymentTermsDays?: number;
};

export const SUPPLIERS_KEY = ["suppliers"] as const;

/**
 * One page of suppliers, optionally narrowed by the search term (api-design:
 * ILIKE on name and document — the term is normalized to digits server-side,
 * so the punctuated CPF/CNPJ a person has in front of them still finds the
 * row stored as digits). The page and the term both go on the wire — the
 * page so every row is reachable (ADR 0019), the term so the server does the
 * filtering (the client only ever holds one page).
 *
 * `init` exists so the server-side seed (ADR 0020) can inject the session
 * cookie header; client callers never pass it.
 */
export async function fetchSuppliers(
  page: number,
  search: string,
  init?: RequestInit,
): Promise<PagedEnvelope<Supplier>> {
  const params = new URLSearchParams({ page: String(page) });
  if (search) {
    params.set("search", search);
  }
  const response = await fetch(
    `${apiBaseUrl()}/api/suppliers?${params.toString()}`,
    {
      ...init,
      credentials: "include",
    },
  );
  if (!response.ok) {
    throw new Error(`GET /api/suppliers -> ${response.status}`);
  }
  return (await response.json()) as PagedEnvelope<Supplier>;
}

/**
 * The one query definition for a page of suppliers: the key carries the page
 * *and* the term (the same shape ADR 0019 gives the page), so each
 * (page, search) pair is its own cache entry and seed and island agree by
 * construction — both go through this factory (ADR 0020).
 */
export function suppliersQuery(
  page: number,
  search: string,
  init?: RequestInit,
) {
  return queryOptions({
    queryKey: [...SUPPLIERS_KEY, page, search],
    queryFn: () => fetchSuppliers(page, search, init),
  });
}
