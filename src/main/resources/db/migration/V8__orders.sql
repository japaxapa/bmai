-- V8: sales orders and their items (ticket 9) — the draft half of
-- business-rules §1. The lifecycle verbs after DRAFT (confirm, processing,
-- complete, cancel, return) arrive with their own tickets; this migration
-- only makes the states nameable and the lines storable.
--
-- The status is a native PG enum, the same convention as user_role: the
-- state machine's vocabulary is the database's too, so a writer that bypasses
-- the API cannot invent a seventh state.
--
-- The number (ORD-YYYY-NNNN, api-design) comes from a sequence rather than
-- COUNT(*) + 1: two concurrent creates must never mint the same number, and a
-- rolled-back draft may leave a gap — gaps are harmless, duplicates are not.
-- The sequence is global, not reset per year; the year is the prefix a person
-- reads, not a second counter to keep consistent. The shape CHECK pins the
-- format for writers that bypass the repository.
--
-- total is the sum of the line subtotals (GLOSSARY "Total"); it is written by
-- the repository on every save, never by the client. A cross-table
-- "total = SUM(items)" rule is not something a CHECK can express, so the
-- aggregate derives it and the column stores the derived answer.
--
-- order_items.subtotal is CHECKed against quantity × unit_price: the same
-- arithmetic the domain performs, as the backstop for a writer that is not
-- the API (console, future import). unit_price is the product's price copied
-- while the order is a DRAFT (GLOSSARY "Price": CONFIRM locks it); quantity
-- must be a positive whole number, the same rule @Positive answers as a 400
-- at the edge. One product may appear once per order — two lines for the
-- same product would make every later stock check ambiguous.

CREATE TYPE order_status AS ENUM (
    'DRAFT', 'CONFIRMED', 'PROCESSING', 'COMPLETED', 'CANCELLED', 'RETURNED'
);

CREATE SEQUENCE orders_number_seq;

CREATE TABLE orders (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number      TEXT NOT NULL,
    customer_id BIGINT NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    status      order_status NOT NULL DEFAULT 'DRAFT',
    total       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX orders_number_unique ON orders (number);
CREATE INDEX orders_customer_created ON orders (customer_id, created_at DESC);
CREATE INDEX orders_status ON orders (status);

ALTER TABLE orders ADD CONSTRAINT orders_number_shape
    CHECK (number ~ '^ORD-[0-9]{4}-[0-9]{4,}$');
ALTER TABLE orders ADD CONSTRAINT orders_total_non_negative
    CHECK (total >= 0);

CREATE TABLE order_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   BIGINT NOT NULL REFERENCES orders (id) ON DELETE RESTRICT,
    product_id BIGINT NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    quantity   INTEGER NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    subtotal   NUMERIC(12, 2) NOT NULL
);

CREATE UNIQUE INDEX order_items_order_product_unique
    ON order_items (order_id, product_id);
CREATE INDEX order_items_order ON order_items (order_id);

ALTER TABLE order_items ADD CONSTRAINT order_items_quantity_positive
    CHECK (quantity > 0);
ALTER TABLE order_items ADD CONSTRAINT order_items_unit_price_positive
    CHECK (unit_price > 0);
ALTER TABLE order_items ADD CONSTRAINT order_items_subtotal_matches
    CHECK (subtotal = quantity * unit_price);
