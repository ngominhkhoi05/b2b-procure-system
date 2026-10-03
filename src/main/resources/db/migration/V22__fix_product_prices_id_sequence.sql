-- ============================================================================
-- V22__fix_product_prices_id_sequence.sql
-- B2B Procure System - Fix product_prices sequence synchronization
-- Database: PostgreSQL
--
-- Problem: "duplicate key value violates unique constraint product_prices_pkey"
-- when inserting a new price tier. The sequence product_prices_id_seq is out
-- of sync with the actual MAX(id) in product_prices.
--
-- Solution: Reset the sequence to the current MAX(id), so next INSERT gets
-- MAX(id) + 1. The 'true' flag tells nextval() that the sequence has been
-- called and the next value should be returned.
-- ============================================================================

SELECT setval(
    'product_prices_id_seq',
    COALESCE((SELECT MAX(id) FROM product_prices), 0),
    true
);
