-- ============================================================================
-- V6__add_unique_constraint_to_cart_items.sql
-- B2B Procure System - Add unique constraint to cart_items(cart_id, product_id)
-- Database: PostgreSQL
-- ============================================================================

ALTER TABLE cart_items ADD CONSTRAINT uk_cart_items_cart_product UNIQUE (cart_id, product_id);
