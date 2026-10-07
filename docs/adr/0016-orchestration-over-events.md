# Orchestrate multi-step flows in one transaction, not events

Confirm, receive, return, and record-payment are each one `@Transactional` application-service method: any step throws, everything rolls back, and one method shows the whole flow. Synchronous domain events would force readers to chase listeners to learn what confirm does — and sync listeners inside the same transaction surprise ("I thought events were decoupled — why did it roll back?"). Events return when modules double, webhooks appear, or fan-out goes async.
