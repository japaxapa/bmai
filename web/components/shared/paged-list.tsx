import type { ReactNode } from "react";

/**
 * The paged envelope api-design promises for every collection — the shape
 * `PagedList` binds to, in one TypeScript home (ADR 0019).
 */
export type PagedEnvelope<T> = {
  items: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
};

type PagedListProps = {
  /** The envelope the API returned — `undefined` while the fetch is in flight. */
  envelope?: PagedEnvelope<unknown>;
  /** Anything truthy means the fetch failed. */
  error?: unknown;
  /** Move to a page (0-based). Wire to `usePageParam().goToPage`. */
  onPageChange: (page: number) => void;
  /** The rows for `envelope.items`, in the list's own markup. */
  children?: ReactNode;
};

/**
 * Everything around a paged list's rows that every list would otherwise
 * re-implement: the loading / empty / error states, the "x–y of total"
 * summary, and the prev/next controls with their bounds (ticket #42,
 * ADR 0019). The rows stay the consumer's — so do the fetch and the query
 * key; this component is fed their result.
 *
 * All counts come from the *returned* envelope: the server's answer about
 * what it actually sent, never the page the client asked for.
 */
export function PagedList({
  envelope,
  error,
  onPageChange,
  children,
}: PagedListProps) {
  if (error) {
    return (
      <p role="alert" className="text-sm text-red-600">
        Could not load this list.
      </p>
    );
  }
  if (!envelope) {
    return <p className="text-sm text-neutral-500">Loading…</p>;
  }
  if (envelope.items.length === 0) {
    return <p className="text-sm text-neutral-500">Nothing here yet.</p>;
  }

  const first = envelope.page * envelope.size + 1;
  const last = Math.min(first + envelope.items.length - 1, envelope.total);
  const singlePage = envelope.totalPages <= 1;

  return (
    <>
      {children}
      <nav
        aria-label="Pagination"
        className="flex items-center gap-3 text-sm text-neutral-500"
      >
        <span>
          {first}–{last} of {envelope.total}
        </span>
        {!singlePage && (
          <>
            <button
              type="button"
              disabled={envelope.page <= 0}
              onClick={() => onPageChange(envelope.page - 1)}
              className="rounded-md border border-neutral-300 px-2 py-1 text-neutral-700 enabled:hover:bg-neutral-100 disabled:opacity-40 dark:border-neutral-700 dark:text-neutral-300 dark:enabled:hover:bg-neutral-900"
            >
              Previous
            </button>
            <button
              type="button"
              disabled={envelope.page >= envelope.totalPages - 1}
              onClick={() => onPageChange(envelope.page + 1)}
              className="rounded-md border border-neutral-300 px-2 py-1 text-neutral-700 enabled:hover:bg-neutral-100 disabled:opacity-40 dark:border-neutral-700 dark:text-neutral-300 dark:enabled:hover:bg-neutral-900"
            >
              Next
            </button>
          </>
        )}
      </nav>
    </>
  );
}
