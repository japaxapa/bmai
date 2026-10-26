"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { RowActions } from "@/components/shared/row-actions";
import { PagedList } from "@/components/shared/paged-list";
import {
  categoriesQuery,
  CATEGORIES_KEY,
  type Category,
} from "@/features/categories/api";
import { usePageParam } from "@/hooks/use-page-param";
import { refusalHandlers } from "@/lib/api/refusal";
import { writeJson } from "@/lib/api/write";
import { useAuth } from "@/providers/auth-provider";

// Shape courtesy only (frontend-architecture §Forms): zod gates an empty
// name before it reaches the API; react-hook-form owns registration, touched
// state and where the messages land. No length cap here — the server owns
// the 120 rule, and its message is the one the field must show.
const categorySchema = z.object({
  name: z.string().min(1, "Name is required."),
});

type CategoryValues = z.infer<typeof categorySchema>;

/**
 * The categories surface: list for everyone, write controls for ADMIN only
 * (business-rules §4) — the backend re-checks every request independently.
 */
export function CategoriesPanel() {
  const { user } = useAuth();
  // UI hides by role — UX only (frontend-architecture §State): the ADMIN-only
  // write gates below mirror business-rules §4, and the backend enforces the
  // same matrix independently on every request.
  const isAdmin = user?.role === "ADMIN";
  const queryClient = useQueryClient();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<CategoryValues>({ defaultValues: { name: "" } });

  const [editing, setEditing] = useState<Category | null>(null);
  const [refusal, setRefusal] = useState<string | null>(null);

  // The page lives in the URL (usePageParam), and the query key carries it:
  // each page is its own cache entry, and the prefix invalidation below
  // still clears all of them after a write.
  const { page, goToPage } = usePageParam();
  const { data, error } = useQuery(categoriesQuery(page));

  // One router for both refusal channels — shared with the products panel
  // (lib/api/refusal.ts), so the routing rule cannot drift panel-to-panel.
  const { handleRefusal, rowActionRefused } = refusalHandlers<CategoryValues>({
    setError,
    setRefusal,
  });

  const create = useMutation({
    mutationFn: (name: string) =>
      writeJson<Category>("/api/categories", "POST", { name }),
    // Only the refetch after the write can bring the new row in, so the list
    // must be invalidated — not optimistically patched.
    onSuccess: async () => {
      setRefusal(null);
      reset({ name: "" });
      await queryClient.invalidateQueries({ queryKey: CATEGORIES_KEY });
    },
    onError: handleRefusal,
  });

  const changeStatus = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      writeJson<Category>(`/api/categories/${id}/status`, "PUT", { active }),
    onSuccess: async () => {
      setRefusal(null);
      await queryClient.invalidateQueries({ queryKey: CATEGORIES_KEY });
    },
    onError: rowActionRefused,
  });

  const rename = useMutation({
    mutationFn: ({ id, name }: { id: number; name: string }) =>
      writeJson<Category>(`/api/categories/${id}`, "PUT", { name }),
    // Close the form and refetch: the row's new name comes back from the
    // API, never from what we typed.
    onSuccess: async () => {
      setEditing(null);
      setRefusal(null);
      reset({ name: "" });
      await queryClient.invalidateQueries({ queryKey: CATEGORIES_KEY });
    },
    onError: handleRefusal,
  });

  const submit = handleSubmit((values) => {
    const parsed = categorySchema.safeParse(values);
    if (!parsed.success) {
      setError("name", { message: parsed.error.issues[0].message });
      return;
    }
    setRefusal(null);
    if (editing) {
      rename.mutate({ id: editing.id, name: parsed.data.name });
    } else {
      create.mutate(parsed.data.name);
    }
  });

  return (
    <main className="flex min-h-full flex-col gap-6 p-6">
      <h1 className="text-2xl font-semibold tracking-tight">Categories</h1>

      {isAdmin && (
        <form onSubmit={submit} noValidate className="flex max-w-sm flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="category-name" className="text-sm font-medium">
              Name
            </label>
            <input
              id="category-name"
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
            disabled={create.isPending || rename.isPending}
            className="h-9 rounded-md bg-neutral-900 text-sm font-medium text-white hover:bg-neutral-700 dark:bg-neutral-100 dark:text-neutral-900"
          >
            {editing ? "Save changes" : "Create category"}
          </button>
        </form>
      )}

      {/* The pager owns everything around the rows — states, summary,
          prev/next bounds (ADR 0019); the rows themselves stay here. */}
      <PagedList envelope={data} error={error} onPageChange={goToPage}>
        <ul className="flex max-w-xl flex-col gap-2">
          {data?.items.map((category) => (
            <li key={category.id} className="flex items-center gap-3">
              <span className="w-44 truncate text-sm">{category.name}</span>
              {/* GLOSSARY "Deactivated": retired rows stay, marked — never gone. */}
              <span className="w-24 text-sm text-neutral-500">
                {category.active ? "Active" : "Deactivated"}
              </span>
              <RowActions
                name={category.name}
                active={category.active}
                canEdit={isAdmin}
                canDeactivate={isAdmin}
                onEdit={() => {
                  // Seed the open form from the row itself — no second
                  // fetch to fill it in (testing-strategy §4).
                  setEditing(category);
                  setRefusal(null);
                  reset({ name: category.name });
                }}
                onToggleStatus={() =>
                  changeStatus.mutate({
                    id: category.id,
                    active: !category.active,
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
