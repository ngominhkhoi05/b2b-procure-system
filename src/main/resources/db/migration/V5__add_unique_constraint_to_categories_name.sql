-- ============================================================================
-- V5__add_unique_constraint_to_categories_name.sql
-- B2B Procure System - Add unique constraint to categories.name
-- Database: PostgreSQL
-- ============================================================================

ALTER TABLE categories ADD CONSTRAINT uk_categories_name UNIQUE (name);
