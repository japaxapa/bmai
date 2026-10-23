"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm, useWatch } from "react-hook-form";
import { PagedList } from "@/components/shared/paged-list";
import {
  CUSTOMERS_KEY,
  customersQuery,
  type Customer,
} from "@/features/customers/api";
import { formatDocument } from "@/lib/format/document";
import { PARTY_EMPTY, partySchema, partyToBody, type PartyValues } from "@/lib/forms/party";
import { refusalHandlers } from "@/lib/api/refusal";
import { writeJson } from "@/lib/api/write";
import { useAuth } from "@/providers/auth-provider";
import { useDebouncedSearch } from "@/hooks/use-debounced-search";
import { usePageParam } from "@/hooks/use-page-param";
/**
 * The customers surface: list for everyone signed in, and the create/edit
 * form too — business-rules §4 is wider here than anywhere else ("Create/edit
 * customer" is ADMIN/MANAGER/EMPLOYEE: the clerk registers the walk-in at the
 * counter). Only the *deactivation* control is ADMIN/MANAGER. UI hides by
 * role, the backend re-checks every request independently
 * (frontend-architecture §State). The party field rules themselves live once
 * in `lib/forms/party.ts`.
 */
export function CustomersPanel() {
  const { user } = useAuth();
  // Business-rules §4's write list, enumerated the way both @PreAuthorize and
  // the supplier/product panels do it: a role outside the list (a future
  // fourth role the API would 403) never sees controls that only 403 on
  // submit — "any signed-in role" would show them.
  const canWrite =
    user?.role === "ADMIN" || user?.role === "MANAGER" || user?.role === "EMPLOYEE";
  // ...but retiring a customer is a supervisor's call (business-rules §4
  // row "Deactivate customer"): the clerk registers and edits the walk-in
  // party, only ADMIN/MANAGER switch one off.
  const canDeactivate = user?.role === "ADMIN" || user?.role === "MANAGER";
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
  const [editing, setEditing] = useState<Customer | null>(null);

  // Page and search term both live in the URL (usePageParam +
  // useDebouncedSearch, ADR 0019): both survive a reload and stay
  // shareable, and the query key carries both — each (page, search) pair
  // is its own cache entry, while the prefix invalidation below clears all
  // of them. The box's own instant-feel clock is the hook's rule, not
  // this panel's.
  const { page, goToPage } = usePageParam();
  const { term, setTerm, search } = useDebouncedSearch();
  const { data, error } = useQuery(customersQuery(page, search));

  // One router for both refusal channels — shared with the categories,
  // products and suppliers panels (lib/api/refusal.ts), so the routing rule
  // cannot drift panel-to-panel.
  const { handleRefusal, rowActionRefused } = refusalHandlers<PartyValues>({
    setError,
    setRefusal,
  });

  const changeStatus = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      writeJson<Customer>(`/api/customers/${id}/status`, "PUT", { active }),
    onSuccess: async () => {
      setRefusal(null);
      await queryClient.invalidateQueries({ queryKey: CUSTOMERS_KEY });
    },
    onError: rowActionRefused,
  });

  const edit = useMutation({
    mutationFn: ({ id, values }: { id: number; values: PartyValues }) =>
      writeJson<Customer>(`/api/customers/${id}`, "PUT", partyToBody(values)),
    // Close the form and refetch: the row's new values come back from the
    // API, never from what we typed.
    onSuccess: async () => {
      setEditing(null);
      setRefusal(null);
      reset(PARTY_EMPTY);
      await queryClient.invalidateQueries({ queryKey: CUSTOMERS_KEY });
    },
    onError: handleRefusal,
  });

  const create = useMutation({
    mutationFn: (values: PartyValues) =>
      writeJson<Customer>("/api/customers", "POST", partyToBody(values)),
    // Only the refetch after the write can bring the new row in, so the list
    // must be invalidated — not optimistically patched.
    onSuccess: async () => {
      setRefusal(null);
      reset(PARTY_EMPTY);
      await queryClient.invalidateQueries({ queryKey: CUSTOMERS_KEY });
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
      <h1 className="text-2xl font-semibold tracking-tight">Customers</h1>

      {/* Reading is open to every signed-in role — so is narrowing the read.
          The server owns the filtering (api-design: ILIKE on name/document). */}
      <div className="flex max-w-xs flex-col gap-1.5">
        <label htmlFor="customer-search" className="text-sm font-medium">
          Search
        </label>
        <input
          id="customer-search"
          value={term}
          onChange={(event) => setTerm(event.target.value)}
          className="h-9 rounded-md border border-neutral-300 px-3 text-sm outline-none focus:border-neutral-500 dark:border-neutral-700"
        />
      </div>

      {canWrite && (
        <form onSubmit={submit} noValidate className="flex max-w-sm flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="customer-name" className="text-sm font-medium">
              Name
            </label>
            <input
              id="customer-name"
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
            <label htmlFor="customer-document" className="text-sm font-medium">
              Document
            </label>
            <input
              id="customer-document"
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
            <label htmlFor="customer-email" className="text-sm font-medium">
              Email
            </label>
            <input
              id="customer-email"
              type="email"
              autoComplete="off"
              className="h-9 rounded-md border border-neutral-300 px-3 text-sm outline-none focus:border-neutral-500 dark:border-neutral-700"
              {...register("email")}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label
              htmlFor="customer-payment-terms"
              className="text-sm font-medium"
            >
              Payment terms (days)
            </label>
            <input
              id="customer-payment-terms"
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
            {editing ? "Save changes" : "Create customer"}
          </button>
        </form>
      )}

      {/* The pager owns everything around the rows — states, summary,
          prev/next bounds (ADR 0019); the rows themselves stay here. */}
      <PagedList envelope={data} error={error} onPageChange={goToPage}>
        <ul className="flex max-w-5xl flex-col gap-2">
          {data?.items.map((customer) => (
            <CustomerRow
              key={customer.id}
              customer={customer}
              canWrite={canWrite}
              canDeactivate={canDeactivate}
              onEdit={() => {
                // Seed the open form from the row itself — no second fetch to
                // fill it in (testing-strategy §4). The document arrives
                // spelled for reading, exactly as the box will keep spelling
                // it.
                setEditing(customer);
                setRefusal(null);
                reset({
                  name: customer.name,
                  document: formatDocument(customer.document),
                  email: customer.email ?? "",
                  paymentTermsDays: String(customer.paymentTermsDays),
                });
              }}
              onToggleStatus={() =>
                changeStatus.mutate({
                  id: customer.id,
                  active: !customer.active,
                })
              }
            />
          ))}
        </ul>
      </PagedList>
    </main>
  );
}

/**
 * One row of the table. The document is formatted on display: the stored
 * digits are canonical, the row spells them the way a person reads a
 * CPF/CNPJ.
 */
function CustomerRow({
  customer,
  canWrite,
  canDeactivate,
  onEdit,
  onToggleStatus,
}: {
  customer: Customer;
  canWrite: boolean;
  canDeactivate: boolean;
  onEdit: () => void;
  onToggleStatus: () => void;
}) {
  return (
    <li className="flex items-center gap-3">
      <span className="w-48 truncate text-sm">{customer.name}</span>
      <span className="w-48 shrink-0 text-sm tabular-nums">
        {formatDocument(customer.document)}
      </span>
      <span className="w-52 truncate text-sm text-neutral-500">
        {customer.email ?? "—"}
      </span>
      <span className="w-20 text-sm tabular-nums">
        Net {customer.paymentTermsDays}
      </span>
      {/* GLOSSARY "Deactivated": retired rows stay, marked — never gone. */}
      <span className="w-24 text-sm text-neutral-500">
        {customer.active ? "Active" : "Deactivated"}
      </span>
      {canWrite && (
        <button
          type="button"
          aria-label={`Edit ${customer.name}`}
          onClick={onEdit}
          className="rounded-md border border-neutral-300 px-2 py-1 text-sm hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-900"
        >
          Edit
        </button>
      )}
      {canDeactivate && (
        <button
          type="button"
          aria-label={`${customer.active ? "Deactivate" : "Reactivate"} ${customer.name}`}
          onClick={onToggleStatus}
          className="rounded-md border border-neutral-300 px-2 py-1 text-sm hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-900"
        >
          {customer.active ? "Deactivate" : "Reactivate"}
        </button>
      )}
    </li>
  );
}
