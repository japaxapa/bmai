"use client";

/**
 * The edit + status-toggle buttons every list row carries — one copy of
 * the aria-labels, role gating and styles (categories, customers, products
 * and suppliers all render the same pair). The columns around them stay
 * per-panel; only the actions are shared.
 *
 * <p>Two independent gates: `canEdit` shows Edit, `canDeactivate` shows
 * Deactivate/Reactivate. Panels where one role gates both pass the same
 * value for both; the customers panel is the one that splits them
 * (business-rules §4: EMPLOYEE edits but does not deactivate).
 */
export function RowActions({
  name,
  active,
  canEdit,
  canDeactivate,
  onEdit,
  onToggleStatus,
}: {
  name: string;
  active: boolean;
  canEdit: boolean;
  canDeactivate: boolean;
  onEdit: () => void;
  onToggleStatus: () => void;
}) {
  return (
    <>
      {canEdit && (
        <button
          type="button"
          aria-label={`Edit ${name}`}
          onClick={onEdit}
          className="rounded-md border border-neutral-300 px-2 py-1 text-sm hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-900"
        >
          Edit
        </button>
      )}
      {canDeactivate && (
        <button
          type="button"
          aria-label={`${active ? "Deactivate" : "Reactivate"} ${name}`}
          onClick={onToggleStatus}
          className="rounded-md border border-neutral-300 px-2 py-1 text-sm hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-900"
        >
          {active ? "Deactivate" : "Reactivate"}
        </button>
      )}
    </>
  );
}
