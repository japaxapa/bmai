import type { FieldPath, FieldValues, UseFormSetError } from "react-hook-form";
import { toast } from "sonner";
import type { Problem } from "@/lib/api/write";

/** Where a panel parks an inline refusal sentence (set by the panel's state). */
type RefusalSink = (message: string | null) => void;

/**
 * The one refusal router every panel shares (api-design, frontend-architecture
 * §Forms): a write refusal lands in one of exactly three places.
 *
 * `handleRefusal` — field messages on the field they name (checked first: a
 * 400 carries *both* a detail sentence and fieldErrors, and the field's own
 * message is the one that must win), any other sentence inline (the AC's
 * "never a raw failure"), anything unrecognised as a toast.
 *
 * `rowActionRefused` — a row action (status flip) has no form to print into,
 * so its refusal takes the toast channel — §Forms' fallback for anything
 * without a field. Writing it into the panel's inline refusal instead would
 * show an unrelated row's failure under the create form, where a reader would
 * take it for the form's own conflict.
 */
export function refusalHandlers<TValues extends FieldValues>({
  setError,
  setRefusal,
}: {
  setError: UseFormSetError<TValues>;
  setRefusal: RefusalSink;
}) {
  const handleRefusal = (problem?: Problem) => {
    if (problem?.fieldErrors) {
      Object.entries(problem.fieldErrors).forEach(([field, messages]) =>
        setError(field as FieldPath<TValues>, { message: messages.join(" ") }),
      );
      return;
    }
    if (problem?.detail) {
      setRefusal(problem.detail);
      return;
    }
    toast.error("The request was refused");
  };

  const rowActionRefused = (problem?: Problem) => {
    toast.error(problem?.detail ?? "The request was refused");
  };

  return { handleRefusal, rowActionRefused };
}
