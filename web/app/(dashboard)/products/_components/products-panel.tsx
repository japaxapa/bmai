"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { PagedList } from "@/components/shared/paged-list";
import { categoriesQuery } from "@/features/categories/api";
import {
  PRODUCTS_KEY,
  productsQuery,
  type Product,
  type ProductBody,
} from "@/features/products/api";
import { suppliersQuery } from "@/features/suppliers/api";
import { useDebouncedSearch } from "@/hooks/use-debounced-search";
import { usePageParam } from "@/hooks/use-page-param";
import { optionalNumber } from "@/lib/forms/optional-number";
import { refusalHandlers } from "@/lib/api/refusal";
import { writeJson } from "@/lib/api/write";
import { useAuth } from "@/providers/auth-provider";

// Shape courtesy only (frontend-architecture §Forms): zod gates the empty
// boxes before they reach the API; react-hook-form owns registration, touched
// state and where the messages land. No caps or ranges here — the server owns
// them (price > 0, cost ≥ 0, minimum stock ≥ 0 — the 422s), and its messages
// are the ones the fields must show.
//
// The optional-number rule (shared with the suppliers panel, lib/forms) is
// shape, not range: a filled box must survive Number(). "1e999" is a
// *valid* type=number value (the spec allows exponents) that Number() turns
// into Infinity — JSON serializes that as null, and a null cost/minimum
// stock is the server's born-zero default: the number the user typed would
// silently become 0 instead of being refused.
export const productSchema = z.object({
  sku: z.string().min(1, "SKU is required."),
  name: z.string().min(1, "Name is required."),
  description: z.string(),
  price: z.string().min(1, "Price is required."),
  cost: optionalNumber("Cost"),
  minimumStock: optionalNumber("Minimum stock"),
  categoryId: z.string().min(1, "A category is required."),
  // The supplier link is optional (ticket #7): a product may be listed
  // before anyone has decided who supplies it, and the empty box is what
  // keeps the id out of the body.
  supplierId: z.string(),
});

type ProductValues = z.infer<typeof productSchema>;

const EMPTY: ProductValues = {
  sku: "",
  name: "",
  description: "",
  price: "",
  cost: "",
  minimumStock: "",
  categoryId: "",
  supplierId: "",
};

/** Form strings → the DTO's numbers; an empty optional box simply stays out. */
function toBody(values: ProductValues): ProductBody {
  return {
    sku: values.sku.trim(),
    name: values.name.trim(),
    description: values.description.trim() || null,
    price: Number(values.price),
    ...(values.cost.trim() === "" ? {} : { cost: Number(values.cost) }),
    ...(values.minimumStock.trim() === ""
      ? {}
      : { minimumStock: Number(values.minimumStock) }),
    categoryId: Number(values.categoryId),
    ...(values.supplierId.trim() === ""
      ? {}
      : { supplierId: Number(values.supplierId) }),
  };
}

/**
 * The products surface: list for everyone signed in, write controls for
 * ADMIN/MANAGER (business-rules §4) — UI hides by role, the backend
 * re-checks every request independently (frontend-architecture §State).
 */
export function ProductsPanel() {
  const { user } = useAuth();
  const canWrite = user?.role === "ADMIN" || user?.role === "MANAGER";
  const queryClient = useQueryClient();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<ProductValues>({ defaultValues: EMPTY });

  const [refusal, setRefusal] = useState<string | null>(null);
  // The row being edited, if any: the same form doubles as create and edit
  // (one record of field rules — create cannot drift from edit).
  const [editing, setEditing] = useState<Product | null>(null);

  // Page and search term both live in the URL (usePageParam +
  // useDebouncedSearch, ADR 0019): both survive a reload and stay
  // shareable, and the query key carries both — each (page, search) pair
  // is its own cache entry, while the prefix invalidation below clears all
  // of them. The box's own instant-feel clock is the hook's rule, not
  // this panel's.
  const { page, goToPage } = usePageParam();
  const { term, setTerm, search } = useDebouncedSearch();
  const { data, error } = useQuery(productsQuery(page, search));
  // The two dropdowns' vocabularies (business-rules §4: reading them is open
  // to every signed-in role precisely so neither dropdown is ever empty).
  const { data: categories } = useQuery(categoriesQuery(0));
  const { data: suppliers } = useQuery(suppliersQuery(0, ""));

  // One router for both refusal channels — shared with the categories panel
  // (lib/api/refusal.ts), so the routing rule cannot drift panel-to-panel.
  const { handleRefusal, rowActionRefused } = refusalHandlers<ProductValues>({
    setError,
    setRefusal,
  });

  const changeStatus = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      writeJson<Product>(`/api/products/${id}/status`, "PUT", { active }),
    onSuccess: async () => {
      setRefusal(null);
      await queryClient.invalidateQueries({ queryKey: PRODUCTS_KEY });
    },
    onError: rowActionRefused,
  });

  const edit = useMutation({
    mutationFn: ({ id, values }: { id: number; values: ProductValues }) =>
      writeJson<Product>(`/api/products/${id}`, "PUT", toBody(values)),
    // Close the form and refetch: the row's new values come back from the
    // API, never from what we typed.
    onSuccess: async () => {
      setEditing(null);
      setRefusal(null);
      reset(EMPTY);
      await queryClient.invalidateQueries({ queryKey: PRODUCTS_KEY });
    },
    onError: handleRefusal,
  });

  const create = useMutation({
    mutationFn: (values: ProductValues) =>
      writeJson<Product>("/api/products", "POST", toBody(values)),
    // Only the refetch after the write can bring the new row in, so the list
    // must be invalidated — not optimistically patched.
    onSuccess: async () => {
      setRefusal(null);
      reset(EMPTY);
      await queryClient.invalidateQueries({ queryKey: PRODUCTS_KEY });
    },
    onError: handleRefusal,
  });

  const submit = handleSubmit((values) => {
    const parsed = productSchema.safeParse(values);
    if (!parsed.success) {
      setError(parsed.error.issues[0].path[0] as keyof ProductValues, {
        message: parsed.error.issues[0].message,
      });
      return;
    }
    setRefusal(null);
    if (editing) {
      edit.mutate({ id: editing.id, values: parsed.data });
    } else {
      create.mutate(parsed.data);
    }
  });

  return (
    <main className="flex min-h-full flex-col gap-6 p-6">
      <h1 className="text-2xl font-semibold tracking-tight">Products</h1>

      {/* Reading is open to every signed-in role — so is narrowing the read.
          The server owns the filtering (api-design: ILIKE on name/sku). */}
      <div className="flex max-w-xs flex-col gap-1.5">
        <label htmlFor="product-search" className="text-sm font-medium">
          Search
        </label>
        <input
          id="product-search"
          value={term}
          onChange={(event) => setTerm(event.target.value)}
          className="h-9 rounded-md border border-neutral-300 px-3 text-sm outline-none focus:border-neutral-500 dark:border-neutral-700"
        />
      </div>

      {canWrite && (
        <form onSubmit={submit} noValidate className="flex max-w-sm flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="product-sku" className="text-sm font-medium">
              SKU
            </label>
            <input
              id="product-sku"
              aria-invalid={Boolean(errors.sku)}
              className="h-9 rounded-md border border-neutral-300 px-3 text-sm outline-none focus:border-neutral-500 dark:border-neutral-700"
              {...register("sku")}
            />
            {errors.sku && (
              <p role="alert" className="text-sm text-red-600">
                {errors.sku.message}
              </p>
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="product-name" className="text-sm font-medium">
              Name
            </label>
            <input
              id="product-name"
              aria-invalid={Boolean(errors.name)}
              className="h-9 rounded-md border border-neutral-300 px-3 text-sm outline-none focus:border-neutral-500 dark:border-neutral-700"
              {...register("name")}
            />
            {errors.name && (
              <p role="alert" className="text-sm text-red-600">
                {errors.name.message}
              </p>
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="product-description" className="text-sm font-medium">
              Description
            </label>
            <input
              id="product-description"
              className="h-9 rounded-md border border-neutral-300 px-3 text-sm outline-none focus:border-neutral-500 dark:border-neutral-700"
              {...register("description")}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="product-price" className="text-sm font-medium">
              Price
            </label>
            <input
              id="product-price"
              type="number"
              step="0.01"
              min="0"
              aria-invalid={Boolean(errors.price)}
              className="h-9 rounded-md border border-neutral-300 px-3 text-sm outline-none focus:border-neutral-500 dark:border-neutral-700"
              {...register("price")}
            />
            {errors.price && (
              <p role="alert" className="text-sm text-red-600">
                {errors.price.message}
              </p>
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="product-cost" className="text-sm font-medium">
              Cost
            </label>
            <input
              id="product-cost"
              type="number"
              step="0.01"
              min="0"
              aria-invalid={Boolean(errors.cost)}
              className="h-9 rounded-md border border-neutral-300 px-3 text-sm outline-none focus:border-neutral-500 dark:border-neutral-700"
              {...register("cost")}
            />
            {errors.cost && (
              <p role="alert" className="text-sm text-red-600">
                {errors.cost.message}
              </p>
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="product-minimum-stock" className="text-sm font-medium">
              Minimum stock
            </label>
            <input
              id="product-minimum-stock"
              type="number"
              min="0"
              aria-invalid={Boolean(errors.minimumStock)}
              className="h-9 rounded-md border border-neutral-300 px-3 text-sm outline-none focus:border-neutral-500 dark:border-neutral-700"
              {...register("minimumStock")}
            />
            {errors.minimumStock && (
              <p role="alert" className="text-sm text-red-600">
                {errors.minimumStock.message}
              </p>
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="product-category" className="text-sm font-medium">
              Category
            </label>
            <select
              id="product-category"
              aria-invalid={Boolean(errors.categoryId)}
              className="h-9 rounded-md border border-neutral-300 px-3 text-sm outline-none focus:border-neutral-500 dark:border-neutral-700"
              {...register("categoryId")}
            >
              <option value="">Choose a category</option>
              {categories?.items.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
            {errors.categoryId && (
              <p role="alert" className="text-sm text-red-600">
                {errors.categoryId.message}
              </p>
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="product-supplier" className="text-sm font-medium">
              Supplier
            </label>
            {/* The one optional link on the form: "No supplier" is the empty
                value, which `toBody` leaves out of the body — the server
                stores null, never a made-up id. */}
            <select
              id="product-supplier"
              className="h-9 rounded-md border border-neutral-300 px-3 text-sm outline-none focus:border-neutral-500 dark:border-neutral-700"
              {...register("supplierId")}
            >
              <option value="">No supplier</option>
              {suppliers?.items.map((supplier) => (
                <option key={supplier.id} value={supplier.id}>
                  {supplier.name}
                </option>
              ))}
            </select>
          </div>

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
            disabled={create.isPending || edit.isPending}
            className="h-9 rounded-md bg-neutral-900 text-sm font-medium text-white hover:bg-neutral-700 dark:bg-neutral-100 dark:text-neutral-900"
          >
            {editing ? "Save changes" : "Create product"}
          </button>
        </form>
      )}

      {/* The pager owns everything around the rows — states, summary,
          prev/next bounds (ADR 0019); the rows themselves stay here. */}
      <PagedList envelope={data} error={error} onPageChange={goToPage}>
        <ul className="flex max-w-3xl flex-col gap-2">
          {data?.items.map((product) => (
            <li key={product.id} className="flex items-center gap-3">
              <span className="w-28 truncate text-sm">{product.sku}</span>
              <span className="w-48 truncate text-sm">{product.name}</span>
              <span className="w-20 text-sm tabular-nums">
                {product.price.toFixed(2)}
              </span>
              <span className="w-28 truncate text-sm text-neutral-500">
                {product.categoryName}
              </span>
              {/* The supplier the product names, resolved by the API (a LEFT
                  join — a product with none still appears, marked "—"). */}
              <span className="w-40 truncate text-sm text-neutral-500">
                {product.supplierName ?? "—"}
              </span>
              {/* GLOSSARY "Deactivated": retired rows stay, marked — never gone. */}
              <span className="w-24 text-sm text-neutral-500">
                {product.active ? "Active" : "Deactivated"}
              </span>
              {canWrite && (
                <>
                  <button
                    type="button"
                    aria-label={`Edit ${product.name}`}
                    onClick={() => {
                      // Seed the open form from the row itself — no second
                      // fetch to fill it in (testing-strategy §4).
                      setEditing(product);
                      setRefusal(null);
                      reset({
                        sku: product.sku,
                        name: product.name,
                        description: product.description ?? "",
                        price: String(product.price),
                        cost: String(product.cost),
                        minimumStock: String(product.minimumStock),
                        categoryId: String(product.categoryId),
                        supplierId: String(product.supplierId ?? ""),
                      });
                    }}
                    className="rounded-md border border-neutral-300 px-2 py-1 text-sm hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-900"
                  >
                    Edit
                  </button>
                  <button
                    type="button"
                    aria-label={`${product.active ? "Deactivate" : "Reactivate"} ${product.name}`}
                    onClick={() =>
                      changeStatus.mutate({
                        id: product.id,
                        active: !product.active,
                      })
                    }
                    className="rounded-md border border-neutral-300 px-2 py-1 text-sm hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-900"
                  >
                    {product.active ? "Deactivate" : "Reactivate"}
                  </button>
                </>
              )}
            </li>
          ))}
        </ul>
      </PagedList>
    </main>
  );
}
