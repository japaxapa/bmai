# FinancialTransaction records cash movement only

`FinancialTransaction` is one actual money movement — INCOME (customer payment, with method) or EXPENSE (supplier payment, opex, refund) — not a reporting copy of invoices. Obligations (Invoice/Payable) carry derived OPEN/PARTIAL/PAID/CREDITED status. This makes the dashboard's invoiced-vs-collected split structural instead of conceptual, and gives "transaction history" one table instead of a UNION.

## Considered options

- Reporting ledger (INCOME row copied per invoice): two tables claiming one fact, two statuses to sync — rejected.
- No unified table: history becomes UNION queries across 4–5 tables — rejected.
