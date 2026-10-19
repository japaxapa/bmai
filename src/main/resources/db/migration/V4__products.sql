-- V4: products table (ticket 6) — the catalog row itself, linked to exactly
-- one category. Also folds in #41: the category-name length cap becomes a
-- database rule, not only an @Size on the request record.
--
-- SKU uniqueness is on lower(sku), mirroring V3's category-name choice
-- (settled with the user): 'CAFE-1' and 'cafe-1' are the same SKU to a
-- person typing them. The index — not a service remembering to check — is
-- what refuses the second spelling.
--
-- The range CHECKs (price > 0, cost ≥ 0, stock ≥ 0, minimum_stock ≥ 0)
-- back the entity guards (ADR 0017): the domain refuses first and answers
-- 422 with a sentence; the database still says no to a caller that is not
-- the API (a console session, a future import).
--
-- stock is the ADR 0006 mirror column: born zero, only ever moved by an
-- inventory movement (ticket #12 on), never by this ticket's form.
--
-- No supplier link yet: suppliers arrive with ticket #7, whose migration
-- adds the FK (database-design's table shape is the end state).

CREATE TABLE products (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku           TEXT NOT NULL,
    name          TEXT NOT NULL,
    description   TEXT,
    price         NUMERIC(12,2) NOT NULL CHECK (price > 0),
    cost          NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (cost >= 0),
    stock         INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
    minimum_stock INTEGER NOT NULL DEFAULT 0 CHECK (minimum_stock >= 0),
    category_id   BIGINT NOT NULL REFERENCES categories (id) ON DELETE RESTRICT,
    active        BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX products_sku_unique ON products (LOWER(sku));
CREATE INDEX products_category_id_idx ON products (category_id);

-- #41: business-rules §5 declares the 1–120 cap; V3 enforced it only
-- through the request DTO. No data risk: every write so far passed the
-- @Size(max = 120) guard.
ALTER TABLE categories ADD CONSTRAINT categories_name_length
    CHECK (char_length(name) BETWEEN 1 AND 120);
