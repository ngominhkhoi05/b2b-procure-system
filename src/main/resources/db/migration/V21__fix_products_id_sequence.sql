-- ============================================================================
-- V21__fix_products_id_sequence.sql
-- B2B Procure System - Fix products sequence synchronization
-- Database: PostgreSQL
--
-- Problem: "duplicate key value violates unique constraint products_pkey"
-- Error occurs when the sequence products_id_seq is out of sync with
-- the actual max id in the products table.
--
-- Solution: Reset the sequence to the current max id in products table.
-- ============================================================================

SELECT setval('products_id_seq', COALESCE((SELECT MAX(id) FROM products), 0), true);
