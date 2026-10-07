# Database design

PostgreSQL + Flyway SQL migrations. Conventions: **bigint identity PKs**, `numeric(12,2)` money, `timestamptz` UTC, native PG enums for status/type/category/method fields, `ON DELETE RESTRICT` everywhere (the trail defends itself).

## Stock design (Q13 + Q20)

- `products.stock INT NOT NULL DEFAULT 0 CHECK (stock >= 0)`, maintained **transactionally with each `inventory_movements` insert**.
- `SUM(movements)` is the audit definition; the column is the fast path. Reconciliation test: column == SUM.
- `available = products.stock − Σ stock_reservations.qty (open)`. No expiry column.

## Tables (15)

- `users` — email **unique** (login identity), password_hash, role enum, active flag.
- `customers`, `suppliers` — profile + `document` **unique** each, email not unique, active flag, `payment_terms_days DEFAULT 30`.
- `categories` — name **unique**.
- `products` — sku **unique**, name, description, price (>0), cost (weighted avg ≥0), stock (≥0), minimum_stock (≥0), category FK, supplier FK, active flag.
- `orders` — customer FK, status enum (DRAFT, CONFIRMED, PROCESSING, COMPLETED, CANCELLED, RETURNED), total, number `ORD-YYYY-NNNN` unique, timestamps.
- `order_items` — order FK, product FK, qty (>0), `unit_price` (locked at CONFIRM), `unit_cost` (written at PROCESSING, nullable until then), subtotal.
- `invoices` — order FK unique (1:1), number `INV-YYYY-NNNN` unique, issue/due dates, total.
- `credit_notes` — invoice FK, amount, date, reason.
- `stock_reservations` — order FK, product FK, qty.
- `inventory_movements` — product FK, signed qty, type enum (PURCHASE, SALE, ADJUSTMENT, RETURN, DAMAGE), reason (required for ADJUSTMENT/DAMAGE), `order_id` / `purchase_order_id` nullable references, created_at.
- `purchase_orders` — supplier FK, status enum, total, number `PO-YYYY-NNNN` unique.
- `purchase_items` — purchase FK, product FK, qty, unit_cost, subtotal.
- `payables` — purchase_order FK unique (1:1), due date, total.
- `financial_transactions` — type INCOME/EXPENSE, category enum (INVENTORY_PURCHASE, REFUND, RENT, SALARIES, UTILITIES, MARKETING, FEES, OTHER), amount (>0), method enum (PIX, TRANSFER, BOLETO, CASH, nullable for opex), `invoice_id` / `payable_id` nullable (at most one), status POSTED/VOID, occurred_on, description.

## Indexes

- All FKs. `orders(customer_id, created_at DESC)`, `orders(status)`. `movements(product_id, created_at)`. `invoices(due_date)`. `financial_transactions(occurred_on)`, `(invoice_id)`, `(payable_id)`.
- Search = `ILIKE` at this scale; pg_trgm → backlog.

## Migration order

users → categories → customers/suppliers → products → orders/items → reservations → movements → purchase orders/items → invoices/credit_notes → payables → financial_transactions → seed (40 products, 20 customers, 5 suppliers, ~40 orders over 60 days, ~10 opex rows).
