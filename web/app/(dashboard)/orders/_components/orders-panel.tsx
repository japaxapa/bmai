"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { PagedList } from "@/components/shared/paged-list";
import { ordersQuery, type OrderSummary } from "@/features/orders/api";
import { formatBRL } from "@/lib/format/money";
import { usePageParam } from "@/hooks/use-page-param";

/**
 * The orders table (ticket #9). Any signed-in role sees it and may create a
 * draft (business-rules §4 gives every role the order flow), so there is no
 * role gate here — unlike the catalog panels, whose writes are narrower.
 *
 * <p>Only a DRAFT row links to its editor: the API refuses every other state
 * with a 409, and the UI must not offer an action the backend will reject.
 */
export function OrdersPanel() {
  const { page, goToPage } = usePageParam();
  const { data, error } = useQuery(ordersQuery(page));

  return (
    <main className="flex min-h-full flex-col gap-6 p-6">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-semibold tracking-tight">Orders</h1>
        <Link
          href="/orders/new"
          className="h-9 rounded-md bg-neutral-900 px-3 text-sm font-medium leading-9 text-white hover:bg-neutral-700 dark:bg-neutral-100 dark:text-neutral-900"
        >
          New order
        </Link>
      </div>

      <PagedList envelope={data} error={error} onPageChange={goToPage}>
        <ul className="flex max-w-5xl flex-col gap-2">
          {data?.items.map((order) => (
            <OrderRow key={order.id} order={order} />
          ))}
        </ul>
      </PagedList>
    </main>
  );
}

function OrderRow({ order }: { order: OrderSummary }) {
  return (
    <li className="flex items-center gap-3">
      <span className="w-36 shrink-0 text-sm tabular-nums">{order.number}</span>
      <span className="w-48 truncate text-sm">{order.customerName}</span>
      <span className="w-28 text-sm text-neutral-500">{order.status}</span>
      <span className="w-28 text-sm tabular-nums">{formatBRL(order.total)}</span>
      {order.status === "DRAFT" && (
        <Link
          href={`/orders/${order.id}/edit`}
          aria-label={`Edit ${order.number}`}
          className="text-sm underline-offset-4 hover:underline"
        >
          Edit
        </Link>
      )}
    </li>
  );
}
