-- ============================================================================
-- V8__add_shipping_snapshot_to_orders.sql
-- B2B Procure System - Add shipping snapshot columns to orders
-- Database: PostgreSQL
-- ============================================================================

ALTER TABLE orders
    ADD COLUMN shipping_company_name VARCHAR,
    ADD COLUMN shipping_phone VARCHAR,
    ADD COLUMN shipping_address VARCHAR;
