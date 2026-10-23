import { queryOptions } from "@tanstack/react-query";
import { apiBaseUrl } from "@/lib/api/base-url";
import type { PartyBody } from "@/lib/forms/party";
import type { PagedEnvelope } from "@/types/paged-envelope";

/**
 * The customers list's fetch and query key — the `features/<f>/api.ts` home
 * frontend-architecture §Structure mandates (same as categories, products and
 * suppliers: the server seed calls these too, and a value export can't come
 * from a `"use client"` module).
 */

/** One row of the party (GLOSSARY Customer). */
export type Customer = {
  id: number;
  name: string;
  /** Canonical form (GLOSSARY): digits only — the punctuation is display. */
  document: string;
  email: string | null;
  paymentTermsDays: number;
  active: boolean;
};

/**
 * The party write shape — one home in `lib/forms/party.ts` (create and edit
 * share it; the features keep the DTO name their modules speak).
 */
export type CustomerBody = PartyBody;

export const CUSTOMERS_KEY = ["customers"] as const;

/**
 * One page of customers, optionally narrowed by the search term (api-design:
 * ILIKE on name and document — the term is normalized to digits server-side,
 * so the punctuated CPF/CNPJ a person has in front of them still finds the
 * row stored as digits). The page and the term both go on the wire — the
 * page so every row is reachable (ADR 0019), the term so the server does the
 * filtering (the client only ever holds one page).
 *
 * `init` exists so the server-side seed (ADR 0020) can inject the session
 * cookie header; client callers never pass it.
 */
export async function fetchCustomers(
  page: number,
  search: string,
  init?: RequestInit,
): Promise<PagedEnvelope<Customer>> {
  const params = new URLSearchParams({ page: String(page) });
  if (search) {
    params.set("search", search);
  }
  const response = await fetch(
    `${apiBaseUrl()}/api/customers?${params.toString()}`,
    {
      ...init,
      credentials: "include",
    },
  );
  if (!response.ok) {
    throw new Error(`GET /api/customers -> ${response.status}`);
  }
  return (await response.json()) as PagedEnvelope<Customer>;
}

/**
 * The one query definition for a page of customers: the key carries the page
 * *and* the term (the same shape ADR 0019 gives the page), so each
 * (page, search) pair is its own cache entry and seed and island agree by
 * construction — both go through this factory (ADR 0020).
 */
export function customersQuery(
  page: number,
  search: string,
  init?: RequestInit,
) {
  return queryOptions({
    queryKey: [...CUSTOMERS_KEY, page, search],
    queryFn: () => fetchCustomers(page, search, init),
  });
}
