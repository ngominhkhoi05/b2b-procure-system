-- ============================================================================
-- V20: Add is_listable column + partial index for BUYER browse
-- ============================================================================
-- Purpose:
--   BUYER's /products page filters: status='ACTIVE' AND category.status='ACTIVE'
--   AND EXISTS at least 1 product_prices row.
--   With 1M rows this filter is slow even with idx_products_status because
--   the EXISTS subquery must execute per-row.
--
--   Adding a denormalized boolean `is_listable` lets us create a partial
--   index that only contains rows visible to BUYER, dramatically reducing
--   index size and making both `searchProducts` data query AND its count
--   query O(log N) over the much smaller index.
--
-- Why a column + trigger instead of a partial index with subquery?
--   Postgres does NOT allow subqueries in partial index WHERE clauses.
--   We materialize the predicate via `is_listable BOOLEAN` and keep it in
--   sync with two triggers (products.status, product_prices INSERT/DELETE).
-- ============================================================================

ALTER TABLE products
    ADD COLUMN is_listable BOOLEAN NOT NULL DEFAULT false;

-- Partial composite index: smallest possible index for BUYER browse.
-- ORDER BY id ASC matches Pageable default sort used by ProductController.
CREATE INDEX idx_products_listable_browse
    ON products (id)
    WHERE is_listable = true;

-- ----------------------------------------------------------------------------
-- Trigger: keep is_listable in sync with status on products.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION products_set_is_listable()
RETURNS TRIGGER AS $$
BEGIN
    -- status check
    IF NEW.status IS DISTINCT FROM 'ACTIVE' THEN
        NEW.is_listable := false;
        RETURN NEW;
    END IF;
    -- price check
    IF EXISTS (SELECT 1 FROM product_prices WHERE product_id = NEW.id) THEN
        NEW.is_listable := true;
    ELSE
        NEW.is_listable := false;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_products_set_is_listable
    BEFORE INSERT OR UPDATE OF status ON products
    FOR EACH ROW
    EXECUTE FUNCTION products_set_is_listable();

-- ----------------------------------------------------------------------------
-- Trigger on product_prices: when a price row is added/deleted, recompute
-- is_listable on the affected product. UPDATE that doesn't change product_id
-- is ignored.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION product_prices_sync_is_listable()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        -- After delete: only update if no more prices remain
        IF NOT EXISTS (SELECT 1 FROM product_prices WHERE product_id = OLD.product_id) THEN
            UPDATE products SET is_listable = false WHERE id = OLD.product_id;
        END IF;
        RETURN OLD;
    ELSE
        -- INSERT or UPDATE: ensure product is markable
        IF (TG_OP = 'INSERT')
           OR (TG_OP = 'UPDATE' AND NEW.product_id IS DISTINCT FROM OLD.product_id) THEN
            UPDATE products SET is_listable = true WHERE id = NEW.product_id AND status = 'ACTIVE';
        END IF;
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_product_prices_sync_is_listable
    AFTER INSERT OR UPDATE OR DELETE ON product_prices
    FOR EACH ROW
    EXECUTE FUNCTION product_prices_sync_is_listable();

-- ----------------------------------------------------------------------------
-- Backfill: existing rows must have correct is_listable.
-- Run as a single UPDATE. For 1M rows this may take a few seconds; run
-- during low traffic if possible.
-- ----------------------------------------------------------------------------
UPDATE products p
SET is_listable = (p.status = 'ACTIVE'
                   AND EXISTS (SELECT 1 FROM product_prices WHERE product_id = p.id));