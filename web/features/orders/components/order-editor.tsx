"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useFieldArray, useForm, useWatch, type FieldPath } from "react-hook-form";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { TableSkeleton } from "@/components/shared/table-skeleton";
import { customersQuery } from "@/features/customers/api";
import { productsQuery } from "@/features/products/api";
import {
  orderQuery,
  ORDERS_KEY,
  type Order,
  type OrderBody,
} from "@/features/orders/api";
import { writeJson, type Problem } from "@/lib/api/write";
import { formatBRL } from "@/lib/format/money";

/**
 * The draft editor, shared by `/orders/new` and `/orders/[id]/edit`
 * (frontend-architecture §Structure). It edits one draft at a time and PUTs
 * the whole item set, because that is the API's shape: adding, removing and
 * re-quantifying are the same verb.
 *
 * <p>Money is shown while typing (quantity × the selected product's price),
 * but the server's answer is what the list and detail render after saving —
 * business-rules §1 "computed by the backend, never trusted from the client".
 *
 * <p>The order is loaded before the form mounts, so its fields are seeded by
 * `defaultValues` rather than by a follow-up effect (the repo's
 * no-useEffect-fetching rule, architecture §8).
 */
export function OrderEditor({ orderId }: { orderId?: number }) {
  const existing = useQuery({
    ...orderQuery(orderId ?? 0),
    enabled: orderId !== undefined,
  });

  if (orderId !== undefined) {
    if (existing.isPending) {
      return <TableSkeleton />;
    }
    if (existing.isError || !existing.data) {
      return (
        <main className="p-6">
          <p role="alert" className="text-sm text-red-600">
            Could not load this order.
          </p>
        </main>
      );
    }
  }

  return <OrderForm order={orderId === undefined ? undefined : existing.data} />;
}

type LineValues = { productId: string; quantity: string };
type OrderValues = { customerId: string; items: LineValues[] };

/**
 * The form itself. It starts with no lines — an empty draft is legal (the API
 * accepts `items: []`), so the salesperson can open the order first and add
 * what the customer asks for next.
 */
function OrderForm({ order }: { order?: Order }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [refusal, setRefusal] = useState<string | null>(null);

  const customers = useQuery(customersQuery(0, ""));
  const products = useQuery(productsQuery(0, ""));

  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<OrderValues>({
    defaultValues: {
      customerId: order ? String(order.customerId) : "",
      items: order
        ? order.items.map((item) => ({
            productId: String(item.productId),
            quantity: String(item.quantity),
          }))
        : [],
    },
  });
  const { fields, append, remove } = useFieldArray({ control, name: "items" });
  const items = useWatch({ control, name: "items" }) ?? [];

  const pricesById = new Map(
    (products.data?.items ?? []).map((product) => [product.id, product]),
  );
  const centsOf = (line: LineValues) => {
    const product = pricesById.get(Number(line.productId));
    if (!product) {
      return 0;
    }
    // Work in cents: 4.10 × 3 must read 12,30, not 12.299999…
    return Math.round(product.price * 100) * Math.max(0, Number(line.quantity) || 0);
  };
  const totalCents = items.reduce((sum, line) => sum + centsOf(line), 0);

  const save = useMutation({
    mutationFn: (body: OrderBody) =>
      order
        ? writeJson<Order>(`/api/orders/${order.id}`, "PUT", body)
        : writeJson<Order>("/api/orders", "POST", body),
    onSuccess: async () => {
      setRefusal(null);
      await queryClient.invalidateQueries({ queryKey: ORDERS_KEY });
      router.push("/orders");
    },
    onError: (problem?: Problem) => {
      // Same routing rule as every panel (lib/api/refusal.ts): a field
      // message lands on the field it names (the API spells nested paths as
      // `items[0].quantity`, which react-hook-form parses), any other
      // sentence inline — a 409/422 has no field to print under.
      if (problem?.fieldErrors) {
        Object.entries(problem.fieldErrors).forEach(([field, messages]) =>
          setError(field as FieldPath<OrderValues>, {
            message: messages.join(" "),
          }),
        );
        return;
      }
      if (problem?.detail) {
        setRefusal(problem.detail);
        return;
      }
      toast.error("The request was refused");
    },
  });

  const submit = handleSubmit((values) => {
    const customerId = Number(values.customerId);
    if (!customerId) {
      setError("customerId", { message: "Choose a customer." });
      return;
    }

    const lines: OrderBody["items"] = [];
    for (const [index, line] of values.items.entries()) {
      const productId = Number(line.productId);
      if (!productId) {
        setError(`items.${index}.productId` as FieldPath<OrderValues>, {
          message: "Choose a product.",
        });
        return;
      }
      const quantity = Number(line.quantity);
      if (!Number.isInteger(quantity) || quantity <= 0) {
        setError(`items.${index}.quantity` as FieldPath<OrderValues>, {
          message: "Quantity must be greater than zero.",
        });
        return;
      }
      lines.push({ productId, quantity });
    }

    setRefusal(null);
    save.mutate({ customerId, items: lines });
  });

  // Both dropdowns are stocked by their own queries, and the running total
  // needs the product prices. Painting the form before they arrive would show
  // empty selects and a R$ 0,00 total for a draft that already has lines —
  // briefly, but wrong. The skeleton is the same one the order load uses.
  if (customers.isPending || products.isPending) {
    return <TableSkeleton />;
  }

  return (
    <main className="flex min-h-full flex-col gap-6 p-6">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-semibold tracking-tight">
          {order ? `Edit ${order.number}` : "New order"}
        </h1>
        <Link
          href="/orders"
          className="text-sm underline-offset-4 hover:underline"
        >
          Back to orders
        </Link>
      </div>

      <form onSubmit={submit} noValidate className="flex max-w-3xl flex-col gap-4">
        <div className="flex max-w-sm flex-col gap-1.5">
          <label htmlFor="order-customer" className="text-sm font-medium">
            Customer
          </label>
          <select
            id="order-customer"
            aria-invalid={Boolean(errors.customerId)}
            className="h-9 rounded-md border border-neutral-300 px-3 text-sm dark:border-neutral-700"
            {...register("customerId")}
          >
            <option value="">Choose a customer</option>
            {customers.data?.items.map((customer) => (
              <option key={customer.id} value={customer.id}>
                {customer.name}
              </option>
            ))}
          </select>
          {errors.customerId && (
            <p role="alert" className="text-sm text-red-600">
              {errors.customerId.message}
            </p>
          )}
        </div>

        <ul className="flex flex-col gap-3">
          {fields.map((field, index) => (
            <li key={field.id} className="flex items-end gap-3">
              <div className="flex w-64 flex-col gap-1.5">
                <label
                  htmlFor={`item-product-${field.id}`}
                  className="text-sm font-medium"
                >
                  Product {index + 1}
                </label>
                <select
                  id={`item-product-${field.id}`}
                  aria-invalid={Boolean(errors.items?.[index]?.productId)}
                  className="h-9 rounded-md border border-neutral-300 px-3 text-sm dark:border-neutral-700"
                  {...register(`items.${index}.productId`)}
                >
                  <option value="">Choose a product</option>
                  {products.data?.items.map((product) => (
                    <option key={product.id} value={product.id}>
                      {product.name}
                    </option>
                  ))}
                </select>
                {errors.items?.[index]?.productId && (
                  <p role="alert" className="text-sm text-red-600">
                    {errors.items[index]?.productId?.message}
                  </p>
                )}
              </div>

              <div className="flex w-28 flex-col gap-1.5">
                <label
                  htmlFor={`item-quantity-${field.id}`}
                  className="text-sm font-medium"
                >
                  Quantity {index + 1}
                </label>
                <input
                  id={`item-quantity-${field.id}`}
                  type="number"
                  min="1"
                  aria-invalid={Boolean(errors.items?.[index]?.quantity)}
                  className="h-9 rounded-md border border-neutral-300 px-3 text-sm tabular-nums dark:border-neutral-700"
                  {...register(`items.${index}.quantity`)}
                />
                {errors.items?.[index]?.quantity && (
                  <p role="alert" className="text-sm text-red-600">
                    {errors.items[index]?.quantity?.message}
                  </p>
                )}
              </div>

              <span className="w-28 pb-2 text-sm tabular-nums">
                {formatBRL(centsOf(items[index] ?? { productId: "", quantity: "0" }) / 100)}
              </span>

              <button
                type="button"
                aria-label={`Remove item ${index + 1}`}
                onClick={() => remove(index)}
                className="h-9 rounded-md border border-neutral-300 px-3 text-sm dark:border-neutral-700"
              >
                Remove
              </button>
            </li>
          ))}
        </ul>

        <div>
          <button
            type="button"
            onClick={() => append({ productId: "", quantity: "1" })}
            className="h-9 rounded-md border border-neutral-300 px-3 text-sm dark:border-neutral-700"
          >
            Add item
          </button>
        </div>

        <p className="text-sm font-medium">
          Total:{" "}
          <span data-testid="order-total" className="tabular-nums">
            {formatBRL(totalCents / 100)}
          </span>
        </p>

        {refusal && (
          <p
            role="alert"
            className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-300"
          >
            {refusal}
          </p>
        )}

        <button
          type="submit"
          disabled={save.isPending}
          className="h-9 w-fit rounded-md bg-neutral-900 px-3 text-sm font-medium text-white hover:bg-neutral-700 dark:bg-neutral-100 dark:text-neutral-900"
        >
          {order ? "Save changes" : "Save draft"}
        </button>
      </form>
    </main>
  );
}
