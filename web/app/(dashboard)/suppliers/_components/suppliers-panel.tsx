"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm, useWatch } from "react-hook-form";
import { RowActions } from "@/components/shared/row-actions";
import { PagedList } from "@/components/shared/paged-list";
import {
  SUPPLIERS_KEY,
  suppliersQuery,
  type Supplier,
} from "@/features/suppliers/api";
import { useDebouncedSearch } from "@/hooks/use-debounced-search";
import { usePageParam } from "@/hooks/use-page-param";
import { formatDocument } from "@/lib/format/document";
import { PARTY_EMPTY, partySchema, partyToBody, type PartyValues } from "@/lib/forms/party";
import { refusalHandlers } from "@/lib/api/refusal";
import { writeJson } from "@/lib/api/write";
import { useAuth } from "@/providers/auth-provider";

/**
 * The suppliers surface: list for everyone signed in, write controls for
 * ADMIN/MANAGER (business-rules §4) — UI hides by role, the backend
 * re-checks every request independently (frontend-architecture §State).
 * The party field rules themselves live once in `lib/forms/party.ts`.
 */
export function SuppliersPanel() {
  const { user } = useAuth();
  const canWrite = user?.role === "ADMIN" || user?.role === "MANAGER";
  const queryClient = useQueryClient();
  const {
    register,
    handleSubmit,
    control,
    setValue,
    reset,
    setError,
    formState: { errors },
  } = useForm<PartyValues>({ defaultValues: PARTY_EMPTY });

  const [refusal, setRefusal] = useState<string | null>(null);
  // The row being edited, if any: the same form doubles as create and edit
  // (one record of field rules — create cannot drift from edit).
  const [editing, setEditing] = useState<Supplier | null>(null);

  // Page and search term both live in the URL (usePageParam +
  // useDebouncedSearch, ADR 0019): both survive a reload and stay
  // shareable, and the query key carries both — each (page, search) pair
  // is its own cache entry, while the prefix invalidation below clears all
  // of them. The box's own instant-feel clock is the hook's rule, not
  // this panel's.
  const { page, goToPage } = usePageParam();
  const { term, setTerm, search } = useDebouncedSearch();
  const { data, error } = useQuery(suppliersQuery(page, search));

  // One router for both refusal channels — shared with the categories and
  // products panels (lib/api/refusal.ts), so the routing rule cannot drift
  // panel-to-panel.
  const { handleRefusal, rowActionRefused } = refusalHandlers<PartyValues>({
    setError,
    setRefusal,
  });

  const changeStatus = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      writeJson<Supplier>(`/api/suppliers/${id}/status`, "PUT", { active }),
    onSuccess: async () => {
      setRefusal(null);
      await queryClient.invalidateQueries({ queryKey: SUPPLIERS_KEY });
    },
    onError: rowActionRefused,
  });

  const edit = useMutation({
    mutationFn: ({ id, values }: { id: number; values: PartyValues }) =>
      writeJson<Supplier>(`/api/suppliers/${id}`, "PUT", partyToBody(values)),
    // Close the form and refetch: the row's new values come back from the
    // API, never from what we typed.
    onSuccess: async () => {
      setEditing(null);
      setRefusal(null);
      reset(PARTY_EMPTY);
      await queryClient.invalidateQueries({ queryKey: SUPPLIERS_KEY });
    },
    onError: handleRefusal,
  });

  const create = useMutation({
    mutationFn: (values: PartyValues) =>
      writeJson<Supplier>("/api/suppliers", "POST", partyToBody(values)),
    // Only the refetch after the write can bring the new row in, so the list
    // must be invalidated — not optimistically patched.
    onSuccess: async () => {
      setRefusal(null);
      reset(PARTY_EMPTY);
      await queryClient.invalidateQueries({ queryKey: SUPPLIERS_KEY });
    },
    onError: handleRefusal,
  });

  const submit = handleSubmit((values) => {
    const parsed = partySchema.safeParse(values);
    if (!parsed.success) {
      setError(parsed.error.issues[0].path[0] as keyof PartyValues, {
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

  // The document box is the one field that formats what it holds (the others
  // are plain text): the mask re-spells each keystroke — CPF up to 11 digits,
  // CNPJ from the 12th on — so the value in the box is presentation and
  // `partyToBody` is the only place that decides what the API is sent.
  const document = useWatch({ control, name: "document" });

  return (
    <main className="flex min-h-full flex-col gap-6 p-6">
      <h1 className="text-2xl font-semibold tracking-tight">Suppliers</h1>

      {/* Reading is open to every signed-in role — so is narrowing the read.
          The server owns the filtering (api-design: ILIKE on name/document). */}
      <div className="flex max-w-xs flex-col gap-1.5">
        <label htmlFor="supplier-search" className="text-sm font-medium">
          Search
        </label>
        <input
          id="supplier-search"
          value={term}
          onChange={(event) => setTerm(event.target.value)}
          className="h-9 rounded-md border border-neutral-300 px-3 text-sm outline-none focus:border-neutral-500 dark:border-neutral-700"
        />
      </div>

      {canWrite && (
        <form onSubmit={submit} noValidate className="flex max-w-sm flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="supplier-name" className="text-sm font-medium">
              Name
            </label>
            <input
              id="supplier-name"
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
            <label htmlFor="supplier-document" className="text-sm font-medium">
              Document
            </label>
            <input
              id="supplier-document"
              inputMode="numeric"
              autoComplete="off"
              aria-invalid={Boolean(errors.document)}
              value={document}
              onChange={(event) =>
                setValue("document", formatDocument(event.target.value))
              }
              className="h-9 rounded-md border border-neutral-300 px-3 text-sm outline-none focus:border-neutral-500 dark:border-neutral-700"
            />
            {errors.document && (
              <p role="alert" className="text-sm text-red-600">
                {errors.document.message}
              </p>
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="supplier-email" className="text-sm font-medium">
              Email
            </label>
            <input
              id="supplier-email"
              type="email"
              autoComplete="off"
              className="h-9 rounded-md border border-neutral-300 px-3 text-sm outline-none focus:border-neutral-500 dark:border-neutral-700"
              {...register("email")}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label
              htmlFor="supplier-payment-terms"
              className="text-sm font-medium"
            >
              Payment terms (days)
            </label>
            <input
              id="supplier-payment-terms"
              type="number"
              min="0"
              autoComplete="off"
              aria-invalid={Boolean(errors.paymentTermsDays)}
              className="h-9 rounded-md border border-neutral-300 px-3 text-sm outline-none focus:border-neutral-500 dark:border-neutral-700"
              {...register("paymentTermsDays")}
            />
            {errors.paymentTermsDays && (
              <p role="alert" className="text-sm text-red-600">
                {errors.paymentTermsDays.message}
              </p>
            )}
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
            {editing ? "Save changes" : "Create supplier"}
          </button>
        </form>
      )}

      {/* The pager owns everything around the rows — states, summary,
          prev/next bounds (ADR 0019); the rows themselves stay here. */}
      <PagedList envelope={data} error={error} onPageChange={goToPage}>
        <ul className="flex max-w-5xl flex-col gap-2">
          {data?.items.map((supplier) => (
            <li key={supplier.id} className="flex items-center gap-3">
              <span className="w-48 truncate text-sm">{supplier.name}</span>
              {/* Format on display: the stored digits are canonical, the row
                  spells them the way a person reads a CPF/CNPJ. */}
              <span className="w-48 shrink-0 text-sm tabular-nums">
                {formatDocument(supplier.document)}
              </span>
              <span className="w-52 truncate text-sm text-neutral-500">
                {supplier.email ?? "—"}
              </span>
              <span className="w-20 text-sm tabular-nums">
                Net {supplier.paymentTermsDays}
              </span>
              {/* GLOSSARY "Deactivated": retired rows stay, marked — never gone. */}
              <span className="w-24 text-sm text-neutral-500">
                {supplier.active ? "Active" : "Deactivated"}
              </span>
              <RowActions
                name={supplier.name}
                active={supplier.active}
                canEdit={canWrite}
                canDeactivate={canWrite}
                onEdit={() => {
                  // Seed the open form from the row itself — no second
                  // fetch to fill it in (testing-strategy §4). The
                  // document arrives spelled for reading, exactly as the
                  // box will keep spelling it.
                  setEditing(supplier);
                  setRefusal(null);
                  reset({
                    name: supplier.name,
                    document: formatDocument(supplier.document),
                    email: supplier.email ?? "",
                    paymentTermsDays: String(supplier.paymentTermsDays),
                  });
                }}
                onToggleStatus={() =>
                  changeStatus.mutate({
                    id: supplier.id,
                    active: !supplier.active,
                  })
                }
              />
            </li>
          ))}
        </ul>
      </PagedList>
    </main>
  );
}
