import { Suspense } from "react";
import { TableSkeleton } from "@/components/shared/table-skeleton";
import { productsQuery } from "@/features/products/api";
import { TableSeed } from "@/lib/api/seed";
import { ProductsPanel } from "./_components/products-panel";

/**
 * Products (ticket #6, seeded per #44 / ADR 0020). The page stays sync and
 * build-safe under `cacheComponents`: it only hands the `searchParams`
 * promise down — the awaits happen inside the Suspense boundary, so the
 * shell (heading, skeleton) still prerenders and the seed streams in.
 */
export default function ProductsPage({ searchParams }: PageProps<"/products">) {
  return (
    <Suspense fallback={<TableSkeleton />}>
      <SeededProducts searchParams={searchParams}>
        <ProductsPanel />
      </SeededProducts>
    </Suspense>
  );
}

type SeedProps = {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
  children: React.ReactNode;
};

/**
 * The search term has to reach the seed *and* the island the same way
 * (ADR 0020's parse-parity rule): the island reads
 * `useSearchParams().get("search")` — first value wins — so this wrapper
 * resolves the same promise and takes the first value the same way before
 * closing the seed factory over it. `TableSeed` then re-awaits the promise
 * for the page number through the same route the categories page uses.
 */
async function SeededProducts({ searchParams, children }: SeedProps) {
  const raw = (await searchParams).search;
  const search = (Array.isArray(raw) ? raw[0] : raw) ?? "";
  return (
    <TableSeed
      query={(page, init) => productsQuery(page, search, init)}
      searchParams={searchParams}
    >
      {children}
    </TableSeed>
  );
}
