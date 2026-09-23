-- ============================================================================
-- V11__normalize_checkout_foundation.sql
-- B2B Procure System - Normalize Database Constraints & Indexes for Checkout
-- Database: PostgreSQL
-- ============================================================================

-- 1. PRODUCTS TABLE
-- Ensure stock_quantity is not null and add invariant check constraints
UPDATE products SET stock_quantity = 0 WHERE stock_quantity IS NULL;

ALTER TABLE products
    ALTER COLUMN stock_quantity SET NOT NULL;

ALTER TABLE products
    ADD CONSTRAINT chk_products_stock_quantity CHECK (stock_quantity >= 0);

ALTER TABLE products
    ADD CONSTRAINT chk_products_stock_vs_reserved CHECK (stock_quantity >= reserved_quantity);

-- 2. ORDER_STATUS_HISTORY TABLE
-- Allow changed_by to be NULL for system-generated actions (e.g. timeout auto-reject)
ALTER TABLE order_status_history
    ALTER COLUMN changed_by DROP NOT NULL;

-- 3. ORDERS TABLE
-- Add check constraint for valid OrderStatus values
ALTER TABLE orders
    ADD CONSTRAINT chk_orders_status CHECK (status IN (
        'PENDING_CONFIRMATION',
        'PAID',
        'CONFIRMED',
        'PREPARING',
        'SHIPPING',
        'COMPLETED',
        'REJECTED',
        'CANCELLED'
    ));

-- Add index on orders(status, created_at) for scheduler timeout and listing queries
CREATE INDEX idx_orders_status_created_at ON orders (status, created_at);

-- 4. PAYMENTS TABLE
-- Add index on payments(status, expired_at) for payment timeout scheduler
CREATE INDEX idx_payments_status_expired_at ON payments (status, expired_at);

-- 5. ORDER_ITEMS TABLE
-- Add check constraints on quantity, unit_price, subtotal
ALTER TABLE order_items
    ADD CONSTRAINT chk_order_items_quantity CHECK (quantity > 0);

ALTER TABLE order_items
    ADD CONSTRAINT chk_order_items_unit_price CHECK (unit_price > 0);

ALTER TABLE order_items
    ADD CONSTRAINT chk_order_items_subtotal CHECK (subtotal >= 0);
