-- ============================================================================
-- V12__add_zalopay_fields_to_payments.sql
-- B2B Procure System - Add ZaloPay fields to payments table
-- Database: PostgreSQL
-- ============================================================================

-- Add app_trans_id column for idempotent callback lookup
ALTER TABLE payments
    ADD COLUMN app_trans_id VARCHAR(40);

-- Add unique constraint for app_trans_id (prevents duplicate callback processing)
ALTER TABLE payments
    ADD CONSTRAINT uk_payments_app_trans_id UNIQUE (app_trans_id);

-- Add index for idempotent callback lookup by ZaloPay transaction ID
CREATE INDEX idx_payments_app_trans_id ON payments (app_trans_id) WHERE app_trans_id IS NOT NULL;
