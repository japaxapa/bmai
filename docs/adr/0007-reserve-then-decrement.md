# Reserve at CONFIRMED, decrement at PROCESSING

CONFIRMED creates `stock_reservations` rows (available = on-hand − reservations, re-checked under product-row lock); PROCESSING-entry releases them and writes SALE movements atomically. Movements record only real stock changes, so the ledger stays honest, at the cost of a second structure and a concurrency story. No reservation expiry — cancel is the release valve.

## Considered options

- Decrement at confirm: simpler, but cancel then needs compensating rows and "cancel with zero ledger effect" disappears.
- Reservation expiry scheduler: real-world correct, portfolio scope creep — documented as a known simplification.
