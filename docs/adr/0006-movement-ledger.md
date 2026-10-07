# Use inventory movements instead of direct stock mutations

Stock is an append-only `inventory_movements` ledger (PURCHASE, SALE, ADJUSTMENT, RETURN, DAMAGE), never an increment/decrement of a bare column. A `products.stock` mirror is maintained transactionally for fast reads and guarded by `CHECK (stock >= 0)`; a reconciliation test asserts it equals `SUM(movements)`. The ledger is the audit trail — rows are never updated or deleted.

## Consequences

- Every stock change carries its reason and reference; history answers "why" as well as "how much".
- Writers must insert movement + mirror update in one transaction; readers use the column, auditors use the ledger.
