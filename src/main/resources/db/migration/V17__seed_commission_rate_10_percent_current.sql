-- ============================================================================
-- V17__seed_commission_rate_10_percent_current.sql
-- B2B Procure System - Seed a 10% commission rate effective from now
--
-- Reason:
--   The default rate seeded by V13 is 5%. Per current business direction
--   we want a new active rate of 10% starting from the moment this
--   migration runs (CURRENT_TIMESTAMP).
--
-- Seed policy:
--   - rate = 10.00 (raw percentage, not fraction — see AGENTS.md §16 and
--     OrderLifecycleServiceImpl line ~388 where the rate is divided by 100
--     before multiplying by subtotal).
--   - effective_from = CURRENT_TIMESTAMP so the row matches the
--     `findActiveRateAt(:completedAt)` filter (`effective_from <= :now`)
--     from this migration forward. Orders completed BEFORE this migration
--     ran will continue to match the V13 row (effective_from = 1970-01-01).
--   - created_by = the seeded admin user (V2).
--
-- Safety:
--   - Idempotent: only inserts if no row with rate = 10.00 already exists,
--     so re-running this migration does not create duplicate active rates.
--   - Does NOT modify or remove the V13 seed row.
--   - Does NOT change schema.
-- ============================================================================

INSERT INTO commission_rates (rate, effective_from, created_at, created_by)
SELECT
    10.00,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    (SELECT id FROM users WHERE username = 'admin')
WHERE NOT EXISTS (
    SELECT 1 FROM commission_rates WHERE rate = 10.00
);