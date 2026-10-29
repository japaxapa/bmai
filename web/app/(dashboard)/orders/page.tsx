import { Suspense } from "react";
import { TableSkeleton } from "@/components/shared/table-skeleton";
import { ordersQuery } from "@/features/orders/api";
import { TableSeed, type TableQueryFactory } from "@/lib/api/seed";
import { OrdersPanel } from "./_components/orders-panel";

/**
 * Orders (ticket #9, seeded per #44 / ADR 0020). The page stays sync and
 * build-safe under `cacheComponents`: it hands the `searchParams` promise
 * down, and the awaits happen inside the Suspense boundary.
 */
export default function OrdersPage({ searchParams }: PageProps<"/orders">) {
  return (
    <Suspense fallback={<TableSkeleton />}>
      <SeededOrders searchParams={searchParams}>
        <OrdersPanel />
      </SeededOrders>
    </Suspense>
  );
}

/**
 * The seed's factory for a URL: page 0 is the same `toPage` the island reads,
 * so seed and island land on the same cache entry by construction (ADR 0020).
 * Orders have no search term yet — api-design's `?status=`/`?customerId=`
 * filters belong to the tickets that need them, so there is nothing here to
 * parse; the parameter every other table's factory takes comes with the first
 * filter.
 */
export function ordersSeedQuery(): TableQueryFactory<
  ReturnType<typeof ordersQuery>["queryKey"]
> {
  return (page, init) => ordersQuery(page, init);
}

type SeedProps = {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
  children: React.ReactNode;
};

function SeededOrders({ searchParams, children }: SeedProps) {
  return (
    <TableSeed query={ordersSeedQuery()} searchParams={searchParams}>
      {children}
    </TableSeed>
  );
}
