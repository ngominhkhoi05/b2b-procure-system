-- ============================================================================
-- V4__make_users_password_nullable.sql
-- B2B Procure System - Make users.password nullable for OAuth2 users
-- Database: PostgreSQL
-- ============================================================================

ALTER TABLE users ALTER COLUMN password DROP NOT NULL;
