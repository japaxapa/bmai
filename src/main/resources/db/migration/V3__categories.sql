-- V3: categories table (ticket 5) — the product vocabulary, ADMIN-managed.
--
-- Uniqueness is on lower(name), not name: the choice settled with the user
-- over a plain UNIQUE, so 'Bebidas' and 'bebidas' are the same word to a
-- person typing them (business-rules §5 "Category name unique"). The index —
-- not a service remembering to check — is what refuses the second spelling.
--
-- No DELETE path: products will reference this table with ON DELETE
-- RESTRICT, so a retired category leaves through active = false and stays in
-- history (GLOSSARY "Deactivated").

CREATE TABLE categories (
    id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name   TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX categories_name_unique ON categories (LOWER(name));
