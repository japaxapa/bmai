-- V5: canonical form for unique text (GLOSSARY "Canonical form") — a unique
-- field is stored trimmed, and lowercased where it is an identifier nobody
-- reads as prose. The entities already canonicalize (Product, Category
-- constructors); these CHECKs are the backstop for a writer that is not the
-- API — the same layering V4 gave the range CHECKs.
--
-- products.sku: trimmed + lowercase (like the login email, V2). Casing and
-- padding could previously invent rows the LOWER(sku) index saw as distinct
-- but a person reads as one SKU ("CAFE-1" vs "cafe-1 ").
--
-- categories.name: trimmed only — a name is display data, identity comes
-- from the lower(name) index. Trailing spaces also padded past #41's
-- char_length cap invisibly.
--
-- The UPDATEs run before the CHECKs and cannot collide: two rows that become
-- equal after lower()/btrim() were already equal to the functional unique
-- indexes before them.

UPDATE products SET sku = LOWER(BTRIM(sku));
UPDATE categories SET name = BTRIM(name);

ALTER TABLE products ADD CONSTRAINT products_sku_canonical
    CHECK (sku = BTRIM(sku) AND sku = LOWER(sku));

ALTER TABLE categories ADD CONSTRAINT categories_name_trimmed
    CHECK (name = BTRIM(name));
