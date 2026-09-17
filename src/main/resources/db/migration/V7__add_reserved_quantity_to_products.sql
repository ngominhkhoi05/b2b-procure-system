-- ============================================================================
-- V7__add_reserved_quantity_to_products.sql
-- B2B Procure System - Add reserved_quantity to products for stock reservation
-- Database: PostgreSQL
-- ============================================================================

ALTER TABLE products
    ADD COLUMN reserved_quantity INT NOT NULL DEFAULT 0;

ALTER TABLE products
    ADD CONSTRAINT chk_products_reserved_quantity CHECK (reserved_quantity >= 0);
