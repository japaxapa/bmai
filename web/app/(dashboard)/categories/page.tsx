import { Suspense } from "react";
import { TableSkeleton } from "@/components/shared/table-skeleton";
import { categoriesQuery } from "@/features/categories/api";
import { TableSeed } from "@/lib/api/seed";
import { CategoriesPanel } from "./_components/categories-panel";

/**
 * Categories (ticket #5, seeded per #44 / ADR 0020). The page stays sync
 * and build-safe under `cacheComponents`: it only hands the `searchParams`
 * promise down — the await happens inside `TableSeed`, within the
 * Suspense boundary, so the shell (heading, skeleton) still prerenders and
 * the seed streams in behind it.
 */
export default function CategoriesPage({ searchParams }: PageProps<"/categories">) {
  return (
    <Suspense fallback={<TableSkeleton />}>
      <TableSeed query={categoriesQuery} searchParams={searchParams}>
        <CategoriesPanel />
      </TableSeed>
    </Suspense>
  );
}
