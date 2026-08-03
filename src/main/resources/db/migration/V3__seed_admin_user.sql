-- =========================================================
-- Adom Community Pharmacy — Permanent Admin User
-- Runs in EVERY environment (dev AND prod) — this file lives in
-- db/migration, not db/dev-migration, specifically so it isn't
-- gated behind the dev profile the way V2's demo data is.
--
-- SECURITY NOTE — read before deploying to a real production environment:
-- The password seeded here ("admin123") is intentionally simple for local
-- testing convenience, per explicit request. Baking a known, weak password
-- into a migration that runs in production is a real risk for a system
-- that handles controlled-substance records — anyone with a copy of this
-- migration file knows the admin credentials. Before this ever reaches a
-- real deployment:
--   1. Log in once as admin@adompharmacy.com / admin123
--   2. Immediately change the password via POST /api/v1/auth/change-password
--   3. Consider a follow-up migration (V4) that flips must_reset_password
--      back to TRUE for this row, so a fresh production database doesn't
--      silently ship with a guessable admin password if step 1-2 get missed.
--
-- phone_number and hire_date are NOT NULL / UNIQUE on the Staff table but
-- weren't specified — placeholder values are used below. Update them to
-- real values before this migration is ever applied against production,
-- since Flyway migrations are immutable once applied (editing this file
-- after the fact means a new migration to correct it, not editing this one).
-- =========================================================

INSERT INTO Staff (
    id, full_name, role, license_number, phone_number, email,
    password_hash, must_reset_password, hire_date, active_status
) VALUES (
    gen_random_uuid(),
    'admin',
    'Admin',
    NULL,
    '0000000000',                 -- placeholder — replace with a real unique phone number
    'admin@adompharmacy.com',
    '$2b$10$Avq.2W0yvIuZRBLODj8nQOkfydiUJoXap400obG2cB9u5g/mB0pwC',  -- bcrypt hash of "admin123"
    FALSE,
    CURRENT_DATE,                 -- placeholder — replace with the real hire date if one matters
    TRUE
);
