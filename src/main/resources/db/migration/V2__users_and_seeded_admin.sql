-- V2: users table + the seeded ADMIN (ticket 3).
--
-- The hash is fixed in SQL on purpose: a fresh clone is demoable with no
-- configuration, and a SQL migration cannot bcrypt an env var. It is
-- BCrypt(10) of the documented default password "admin123"; the
-- AdminSeeder component overrides email/password from ADMIN_EMAIL /
-- ADMIN_PASSWORD when those are set, so the fixed row is only the default.
--
-- Emails are the login identity, so they are stored lowercase and unique.

CREATE TYPE user_role AS ENUM ('ADMIN', 'MANAGER', 'EMPLOYEE');

CREATE TABLE users (
    id            BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         TEXT        NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    role          user_role   NOT NULL,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT users_email_lowercase CHECK (email = LOWER(email))
);

INSERT INTO users (email, password_hash, role, active)
VALUES ('admin@bms.local', '$2a$10$Iyh8z8RUE4KSbsMOhhX/Hey0QDiMgeUsqGNmZAEfkdODOOQY1i2gC', 'ADMIN', TRUE);
