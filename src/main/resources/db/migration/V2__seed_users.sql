-- ============================================================================
-- V2__seed_users.sql
-- B2B Procure System - Seed Initial Users & Companies
-- Database: PostgreSQL
-- Default Password for all accounts: password123
-- BCrypt Hash: $2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy
-- ============================================================================

-- ============================================================================
-- 1. SEED SAMPLE COMPANIES (for BUYER & SUPPLIER)
-- ============================================================================
INSERT INTO companies (name, tax_code, email, phone, address, company_type, status, created_at, updated_at)
VALUES
('B2B Retail Corporation', '0101234567', 'contact@b2bretail.com', '0901234567', '123 Nguyen Trai, Ha Noi', 'BUYER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('B2B Wholesale Supplies Co.', '0107654321', 'contact@b2bwholesale.com', '0907654321', '456 Le Duan, Da Nang', 'SUPPLIER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (tax_code) DO NOTHING;

-- ============================================================================
-- 2. SEED 3 INITIAL USERS
-- Accounts:
-- 1. admin@gmail.com    | username: admin    | role: ADMIN    | password: password123
-- 2. buyer@gmail.com    | username: buyer    | role: BUYER    | password: password123
-- 3. supplier@gmail.com | username: supplier | role: SUPPLIER | password: password123
-- ============================================================================
INSERT INTO users (
    role_id,
    company_id,
    username,
    password,
    full_name,
    email,
    phone,
    status,
    created_at,
    updated_at
)
VALUES
(
    (SELECT id FROM roles WHERE name = 'ADMIN'),
    NULL,
    'admin',
    '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy',
    'System Administrator',
    'admin@gmail.com',
    '0900000001',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    (SELECT id FROM roles WHERE name = 'BUYER'),
    (SELECT id FROM companies WHERE tax_code = '0101234567'),
    'buyer',
    '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy',
    'Buyer Manager',
    'buyer@gmail.com',
    '0900000002',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    (SELECT id FROM roles WHERE name = 'SUPPLIER'),
    (SELECT id FROM companies WHERE tax_code = '0107654321'),
    'supplier',
    '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy',
    'Supplier Manager',
    'supplier@gmail.com',
    '0900000003',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
