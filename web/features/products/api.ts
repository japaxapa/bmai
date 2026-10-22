import { queryOptions } from "@tanstack/react-query";
import { apiBaseUrl } from "@/lib/api/base-url";
import type { PagedEnvelope } from "@/types/paged-envelope";

/**
 * The products list's fetch and query key — the `features/<f>/api.ts` home
 * frontend-architecture §Structure mandates (same as categories: the server
 * seed calls these too, and a value export can't come from a `"use client"`
 * module).
 */

/** One row of the catalog (GLOSSARY Product). */
export type Product = {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  price: number;
  cost: number;
  minimumStock: number;
  stock: number;
  categoryId: number;
  categoryName: string;
  /**
   * The optional supplier link (#7): null on both sides when a product has
   * none — the id an edit posts back, the name the row displays, and no
   * second request to join them (same pair the category travels as).
   */
  supplierId: number | null;
  supplierName: string | null;
  active: boolean;
};

/**
 * What the write DTO takes (create and edit share the whole shape).
 * `supplierId` is optional: a product is born supplier-less, and an omitted
 * id is "no supplier" — never a sentinel number to filter out.
 */
export type ProductBody = {
  sku: string;
  name: string;
  description: string | null;
  price: number;
  cost?: number;
  minimumStock?: number;
  categoryId: number;
  supplierId?: number;
};

export const PRODUCTS_KEY = ["products"] as const;

/**
 * One page of products, optionally narrowed by the search term (api-design:
 * ILIKE on name and sku). The page and the term both go on the wire — the
 * page so every row is reachable (ADR 0019), the term so the server does
 * the filtering (the client only ever holds one page).
 *
 * `init` exists so the server-side seed (ADR 0020) can inject the session
 * cookie header; client callers never pass it.
 */
export async function fetchProducts(
  page: number,
  search: string,
  init?: RequestInit,
): Promise<PagedEnvelope<Product>> {
  const params = new URLSearchParams({ page: String(page) });
  if (search) {
    params.set("search", search);
  }
  const response = await fetch(`${apiBaseUrl()}/api/products?${params.toString()}`, {
    ...init,
    credentials: "include",
  });
  if (!response.ok) {
    throw new Error(`GET /api/products -> ${response.status}`);
  }
  return (await response.json()) as PagedEnvelope<Product>;
}

/**
 * The one query definition for a page of products: the key carries the page
 * *and* the term (the same shape ADR 0019 gives the page), so each
 * (page, search) pair is its own cache entry and seed and island agree by
 * construction — both go through this factory (ADR 0020).
 */
export function productsQuery(
  page: number,
  search: string,
  init?: RequestInit,
) {
  return queryOptions({
    queryKey: [...PRODUCTS_KEY, page, search],
    queryFn: () => fetchProducts(page, search, init),
  });
}
