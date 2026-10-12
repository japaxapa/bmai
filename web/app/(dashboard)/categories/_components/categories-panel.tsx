"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { apiBaseUrl } from "@/lib/api/base-url";
import { useAuth } from "@/providers/auth-provider";

/** What the API refuses with: RFC 9457, possibly carrying field errors. */
type Problem = {
  status?: number;
  detail?: string;
  fieldErrors?: Record<string, string[]>;
};

async function refusalFrom(response: Response): Promise<Problem> {
  return (await response.json().catch(() => ({}))) as Problem;
}

// Shape courtesy only (frontend-architecture §Forms): zod gates an empty
// name before it reaches the API; react-hook-form owns registration, touched
// state and where the messages land. No length cap here — the server owns
// the 120 rule, and its message is the one the field must show.
const categorySchema = z.object({
  name: z.string().min(1, "Name is required."),
});

type CategoryValues = z.infer<typeof categorySchema>;

/** One row of the vocabulary (GLOSSARY Category). */
export type Category = { id: number; name: string; active: boolean };

/** The paged envelope api-design promises for every collection. */
export type CategoriesEnvelope = {
  items: Category[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
};

export const CATEGORIES_KEY = ["categories"] as const;

export async function fetchCategories(): Promise<CategoriesEnvelope> {
  const response = await fetch(`${apiBaseUrl()}/api/categories`, {
    credentials: "include",
  });
  if (!response.ok) {
    throw new Error(`GET /api/categories -> ${response.status}`);
  }
  return (await response.json()) as CategoriesEnvelope;
}

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
  const [conflict, setConflict] = useState<string | null>(null);

  const { data } = useQuery({
    queryKey: CATEGORIES_KEY,
    queryFn: fetchCategories,
  });

  /**
   * One place a write refusal lands: field messages on the field they name
   * (checked first — a 400 carries both a detail sentence and fieldErrors,
   * and the field's own message is the one that must win), any other
   * sentence inline (the AC's "never a raw failure"), anything unrecognised
   * as a toast (frontend-architecture §Forms).
   */
  const handleRefusal = (problem?: Problem) => {
    if (problem?.fieldErrors) {
      Object.entries(problem.fieldErrors).forEach(([field, messages]) =>
        setError(field as keyof CategoryValues, { message: messages.join(" ") }),
      );
      return;
    }
    if (problem?.detail) {
      setConflict(problem.detail);
      return;
    }
    toast.error("The request was refused");
  };

  const create = useMutation({
    mutationFn: async (name: string) => {
      const response = await fetch(`${apiBaseUrl()}/api/categories`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ name }),
      });
      if (!response.ok) {
        throw await refusalFrom(response);
      }
      return (await response.json()) as Category;
    },
    // Only the refetch after the write can bring the new row in, so the list
    // must be invalidated — not optimistically patched.
    onSuccess: async () => {
      setConflict(null);
      reset({ name: "" });
      await queryClient.invalidateQueries({ queryKey: CATEGORIES_KEY });
    },
    onError: handleRefusal,
  });

  const changeStatus = useMutation({
    mutationFn: async ({ id, active }: { id: number; active: boolean }) => {
      const response = await fetch(
        `${apiBaseUrl()}/api/categories/${id}/status`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify({ active }),
        },
      );
      if (!response.ok) {
        throw await refusalFrom(response);
      }
      return (await response.json()) as Category;
    },
    onSuccess: async () => {
      setConflict(null);
      await queryClient.invalidateQueries({ queryKey: CATEGORIES_KEY });
    },
    onError: handleRefusal,
  });

  const rename = useMutation({
    mutationFn: async ({ id, name }: { id: number; name: string }) => {
      const response = await fetch(`${apiBaseUrl()}/api/categories/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ name }),
      });
      if (!response.ok) {
        throw await refusalFrom(response);
      }
      return (await response.json()) as Category;
    },
    // Close the form and refetch: the row's new name comes back from the
    // API, never from what we typed.
    onSuccess: async () => {
      setEditing(null);
      setConflict(null);
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
    setConflict(null);
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
          {conflict && (
            <p role="alert" className="text-sm text-red-600">
              {conflict}
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

      <ul className="flex max-w-xl flex-col gap-2">
        {data?.items.map((category) => (
          <li key={category.id} className="flex items-center gap-3">
            <span className="w-44 truncate text-sm">{category.name}</span>
            {/* GLOSSARY "Deactivated": retired rows stay, marked — never gone. */}
            <span className="w-24 text-sm text-neutral-500">
              {category.active ? "Active" : "Deactivated"}
            </span>
            {isAdmin && (
              <>
                <button
                  type="button"
                  aria-label={`Edit ${category.name}`}
                  onClick={() => {
                    // Seed the open form from the row itself — no second
                    // fetch to fill it in (testing-strategy §4).
                    setEditing(category);
                    setConflict(null);
                    reset({ name: category.name });
                  }}
                  className="rounded-md border border-neutral-300 px-2 py-1 text-sm hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-900"
                >
                  Edit
                </button>
                <button
                  type="button"
                  aria-label={`${category.active ? "Deactivate" : "Reactivate"} ${category.name}`}
                  onClick={() =>
                    changeStatus.mutate({
                      id: category.id,
                      active: !category.active,
                    })
                  }
                  className="rounded-md border border-neutral-300 px-2 py-1 text-sm hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-900"
                >
                  {category.active ? "Deactivate" : "Reactivate"}
                </button>
              </>
            )}
          </li>
        ))}
      </ul>
    </main>
  );
}
