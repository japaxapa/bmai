-- V6: suppliers table (ticket 7) — the party we buy from, and the link from
-- a product to it. V4 deliberately left that link out ("No supplier link yet:
-- suppliers arrive with ticket #7"); this is where it arrives.
--
-- document is stored in canonical form (GLOSSARY "Canonical form"): digits
-- only, because the punctuation a person types is presentation. The CHECK is
-- the shape half of the domain rule (11 digits CPF or 14 digits CNPJ); the
-- *check digits* are refused by the entity (ADR 0017) — a modulus-11
-- checksum is arithmetic, not something Postgres can express without a
-- function, and every write path funnels through the constructor anyway.
--
-- The unique index is on document exactly, not lower(document): a CPF has no
-- case, and its digits are already canonical — there is nothing for a
-- case-insensitive comparison to collapse. Two spellings of one document
-- (11.222.333/0001-81 vs 11222333000181) collide because the API strips the
-- punctuation *before* the index ever sees them, not because the index is
-- clever. That is the 409 path the E2E test drives.
--
-- email is deliberately NOT unique: contact info is not identity (a company
-- can give us several addresses, and the same address can serve two CNPJs of
-- one group).
--
-- payment_terms_days DEFAULT 30 is business-rules §3: an empty box in the
-- form means thirty days, and the database — not the service remembering to
-- fill in a number — is what makes that true for every writer, including a
-- future import.

CREATE TABLE suppliers (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name               TEXT NOT NULL,
    document           TEXT NOT NULL,
    email              TEXT,
    payment_terms_days INTEGER NOT NULL DEFAULT 30 CHECK (payment_terms_days >= 0),
    active             BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX suppliers_document_unique ON suppliers (document);

-- #41's precedent: a length cap is a database rule, not only an @Size on the
-- request record — a console session and a future import obey it too.
ALTER TABLE suppliers ADD CONSTRAINT suppliers_name_length
    CHECK (char_length(name) BETWEEN 1 AND 120);
ALTER TABLE suppliers ADD CONSTRAINT suppliers_document_length
    CHECK (char_length(document) IN (11, 14));

-- The product ↔ supplier link (database-design's table shape is the end
-- state). Nullable: a product may be listed before anyone has decided who
-- supplies it, and the stock-movement ticket later needs the row to stay
-- put — hence ON DELETE RESTRICT, like category_id, so retiring a supplier
-- is a status flip (the GLOSSARY "Deactivated" idea), never a silent break
-- of history.
ALTER TABLE products
    ADD COLUMN supplier_id BIGINT REFERENCES suppliers (id) ON DELETE RESTRICT;

CREATE INDEX products_supplier_id_idx ON products (supplier_id);
