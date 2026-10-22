-- V7: customers table (ticket 8) — the party that buys from us, the
-- counterpart of the supplier (V6) on the sell side. Same party shape by
-- domain (database-design: "profile + document unique, email not unique,
-- active flag, payment_terms_days DEFAULT 30"), so every V6 choice is
-- re-stated here on purpose rather than shared: each migration is a frozen
-- step in the trail.
--
-- document is stored in canonical form (GLOSSARY "Canonical form"): digits
-- only. The punctuation a person types is presentation, and the unique index
-- only ever sees the digits — two spellings of one party collide *before*
-- the index, because the entity strips the punctuation on the way in. That
-- is the 409 path the E2E test drives.
--
-- email is deliberately NOT unique: contact info is not identity (GLOSSARY
-- "Document") — the same address can serve a head office and its branch.
--
-- The CHECKs below are the shape half of the domain rules (V6's exact
-- choices, restated): 11 digits is a CPF, 14 a CNPJ, anything else is not a
-- document — and the name is 1–120 chars, the same cell the category and
-- supplier tables carry (business-rules §5). The *check digits* are the
-- entity's arithmetic (ADR 0017) — a modulus-11 checksum is not something
-- Postgres can express without a function — and every API write path funnels
-- through the constructor anyway. The CHECKs stay as the backstop for a
-- writer that is not the API (console session, future import). The terms
-- CHECK is the range half of business-rules §3: negative days is not a
-- payment window — the entity refuses it as a 422 first, so this never
-- answers an unhandled 500 to an API caller.
--
-- payment_terms_days DEFAULT 30 is business-rules §3: an empty box in the
-- form means thirty days, and the database — not the service remembering to
-- fill in a number — is what makes that true for every writer.

CREATE TABLE customers (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name               TEXT NOT NULL,
    document           TEXT NOT NULL,
    email              TEXT,
    payment_terms_days INTEGER NOT NULL DEFAULT 30,
    active             BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX customers_document_unique ON customers (document);

ALTER TABLE customers ADD CONSTRAINT customers_name_length
    CHECK (char_length(name) BETWEEN 1 AND 120);
ALTER TABLE customers ADD CONSTRAINT customers_document_length
    CHECK (char_length(document) IN (11, 14));
ALTER TABLE customers ADD CONSTRAINT customers_payment_terms_range
    CHECK (payment_terms_days >= 0);
