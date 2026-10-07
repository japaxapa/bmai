# Cancel and return are different operations

CANCELLED is reachable from DRAFT/CONFIRMED only and writes zero ledger rows (CONFIRMED-cancel releases the reservation). Anything after stock moved is not cancellable; a post-COMPLETED reversal is a **return** operation producing +RETURN movements, a RETURNED state, and a credit note. One word per ledger consequence keeps "what does cancel do?" answerable.

## Consequences

- DRAFT-cancel is a status flip; PROCESSING is uncancellable; supplier-side post-receipt removal stays ADJUSTMENT-with-reason.
