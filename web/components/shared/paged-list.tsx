import type { ReactNode } from "react";
import type { PagedEnvelope } from "@/types/paged-envelope";

// The contract travels with the component for import ergonomics; its home is
// types/ (architecture.md §7, DTO mirrors).
export type { PagedEnvelope } from "@/types/paged-envelope";

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

/** One definition of the pager's button: the shared skin, never a link. */
function PagerButton({
  disabled,
  onClick,
  children,
}: {
  disabled?: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className="rounded-md border border-neutral-300 px-2 py-1 text-neutral-700 enabled:hover:bg-neutral-100 disabled:opacity-40 dark:border-neutral-700 dark:text-neutral-300 dark:enabled:hover:bg-neutral-900"
    >
      {children}
    </button>
  );
}

/**
 * Everything around a paged list's rows that every list would otherwise
 * re-implement: the loading / empty / error states, the "x–y of total"
 * summary, and the prev/next controls with their bounds (ticket #42,
 * ADR 0019). The rows stay the consumer's — so do the fetch and the query
 * key; this component is fed their result.
 *
 * All counts come from the *returned* envelope: the server's answer about
 * what it actually sent, never the page the client asked for.
 *
 * Two empties, told apart by the envelope. `total === 0` is a genuinely
 * empty list — the message, no controls. But zero items on a page of a list
 * that *has* rows means the URL outlived the list (a stale or hand-edited
 * `?page=`; the backend answers the truth rather than clamping). Prev/Next
 * would only step through more dead pages, so the way back is one jump to
 * the last real page. Page numbers are displayed 1-based — humans count
 * from 1 — while the URL and the API stay 0-based (ADR 0019).
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
  if (envelope.total === 0) {
    return <p className="text-sm text-neutral-500">Nothing here yet.</p>;
  }
  if (envelope.items.length === 0) {
    return (
      <nav
        aria-label="Pagination"
        className="flex items-center gap-3 text-sm text-neutral-500"
      >
        <span>
          Page {envelope.page + 1} of {envelope.totalPages}
        </span>
        <PagerButton onClick={() => onPageChange(envelope.totalPages - 1)}>
          Go to last page
        </PagerButton>
      </nav>
    );
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
            <PagerButton
              disabled={envelope.page <= 0}
              onClick={() => onPageChange(envelope.page - 1)}
            >
              Previous
            </PagerButton>
            <PagerButton
              disabled={envelope.page >= envelope.totalPages - 1}
              onClick={() => onPageChange(envelope.page + 1)}
            >
              Next
            </PagerButton>
          </>
        )}
      </nav>
    </>
  );
}
