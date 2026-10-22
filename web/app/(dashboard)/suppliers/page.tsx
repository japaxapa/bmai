import { Suspense } from "react";
import { TableSkeleton } from "@/components/shared/table-skeleton";
import { suppliersQuery } from "@/features/suppliers/api";
import { paramFromSearchParams, TableSeed, type TableQueryFactory } from "@/lib/api/seed";
import { SuppliersPanel } from "./_components/suppliers-panel";

/**
 * Suppliers (ticket #7, seeded per #44 / ADR 0020). The page stays sync and
 * build-safe under `cacheComponents`: it only hands the `searchParams`
 * promise down — the awaits happen inside the Suspense boundary, so the
 * shell (heading, skeleton) still prerenders and the seed streams in.
 */
export default function SuppliersPage({ searchParams }: PageProps<"/suppliers">) {
  return (
    <Suspense fallback={<TableSkeleton />}>
      <SeededSuppliers searchParams={searchParams}>
        <SuppliersPanel />
      </SeededSuppliers>
    </Suspense>
  );
}

/**
 * The seed's query factory for a URL — the parse-parity rule as one
 * jsdom-callable function (ADR 0020; the seed suite's seam, same as
 * `seedTable` itself): the search term is read through the *same*
 * `paramFromSearchParams` the island's `useSearchParam` answers to (a
 * URLSearchParams `.get`, first value wins), then closed over the island's
 * own `suppliersQuery`. Seed and island therefore land on the same cache
 * entry by construction, and the seed suite pins that construction here —
 * not a re-implementation of it.
 */
export function suppliersSeedQuery(
  searchParams: Record<string, string | string[] | undefined>,
): TableQueryFactory<(string | number)[]> {
  const search = paramFromSearchParams(searchParams, "search") ?? "";
  return (page, init) => suppliersQuery(page, search, init);
}

type SeedProps = {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
  children: React.ReactNode;
};

/**
 * The RSC wrapper over `suppliersSeedQuery`: await the URL inside the
 * caller's `<Suspense>` (so the route keeps its static shell), seed through
 * the shared factory above, hand the island the boundary that carries the
 * result. `TableSeed` re-awaits the promise for the page number through the
 * same route the products page uses.
 */
async function SeededSuppliers({ searchParams, children }: SeedProps) {
  const params = await searchParams;
  return (
    <TableSeed query={suppliersSeedQuery(params)} searchParams={searchParams}>
      {children}
    </TableSeed>
  );
}
