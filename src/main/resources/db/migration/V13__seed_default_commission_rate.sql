-- ============================================================================
-- V13__seed_default_commission_rate.sql
-- B2B Procure System - Seed a default commission rate
--
-- Reason:
--   The `commission_rates` table was created in V1 with no seed rows.
--   Completing an Order via `OrderLifecycleServiceImpl.updateToCompleted`
--   queries `findActiveRateAt(:now)` and throws
--   COMMISSION_RATE_NOT_FOUND when no row has `effective_from <= now`.
--
--   Without a seed, suppliers cannot complete any order until an ADMIN
--   manually creates one via the admin Commission Rate API. This blocks
--   the lifecycle end-to-end on a fresh database.
--
-- Seed policy:
--   - Insert ONE default rate of 5.00% (raw percentage, not fraction —
--     see AGENTS.md §16 and OrderLifecycleServiceImpl line ~388 where
--     the rate is divided by 100 before multiplying by subtotal).
--   - effective_from = '1970-01-01 00:00:00' so the row matches the
--     `findActiveRateAt` filter (`effective_from <= :completedAt`) for ANY
--     historical, current, and future timestamp. This is critical because
--     the runtime filter is `c.effectiveFrom <= :completedAt` — using
--     CURRENT_TIMESTAMP would NOT cover orders completed before V13 runs.
--   - created_by = the seeded admin user (V2).
--
-- Safety:
--   - Idempotent: uses WHERE NOT EXISTS so re-running this migration
--     (or V13 re-application) does not create duplicates.
--   - Does NOT modify any existing rows.
--   - Does NOT change schema.
-- ============================================================================

INSERT INTO commission_rates (rate, effective_from, created_at, created_by)
SELECT
    5.00,
    TIMESTAMP '1970-01-01 00:00:00',
    CURRENT_TIMESTAMP,
    (SELECT id FROM users WHERE username = 'admin')
WHERE NOT EXISTS (
    SELECT 1 FROM commission_rates
);
