-- ============================================================================
-- V16__add_product_fulltext_search.sql
-- B2B Procure System - Full-text search for products (1M scale)
--
-- Why:
--   Pre-V16 search used JPQL LOWER(...) LIKE '%keyword%' which forces a full
--   table scan + lowercase on 3 columns per row. With 1M products this is
--   unacceptable latency.
--
--   This migration adds a tsvector generated column over (name, sku, description)
--   and a single GIN index so websearch_to_tsquery() can use index lookups
--   instead of scans. Weighting: name (A) > sku (B) > description (C).
--
-- Why a custom text search config 'vn_simple'?
--   Postgres has no 'vietnamese' built-in. The 'english' config applies English
--   stemming (running -> run) which is meaningless for product names. The
--   'simple' config lowercases but does NOT strip diacritics, so "hộp" != "hop".
--
--   to_tsvector() only accepts a regconfig (configuration name), not a dict
--   name. So we have to define a config that maps words through unaccent.
--
--   vn_simple = COPY of 'simple' + dictionary chain (unaccent, simple) on
--   word/hword/hword_part tokens. This gives us lowercase + diacritic-stripping
--   without any stemming.
--
--   Users searching "hop" will match a product named "hộp quà".
--   Users searching "iPhone" will match a product named "iphone 15".
--
-- Safety:
--   - Additive only. One new column (GENERATED STORED) + one GIN index.
--   - No data migration needed; Postgres materializes it as rows are read/written.
--   - However, for tables already containing 1M+ rows, Postgres must compute
--     the tsvector on every existing row when the column is added. This is
--     a one-time cost and may take a few minutes on large tables.
--
-- Operational notes:
--   - CREATE EXTENSION requires superuser or DB owner privileges.
--     Supabase: granted by default. RDS: must be enabled in the parameter group.
--   - The GIN index build on 1M rows can take several minutes and locks the
--     table with SHARE lock. If applying during business hours, consider
--     running CREATE INDEX CONCURRENTLY manually (outside this migration
--     transaction) and then add the index creation here as a no-op.
--   - After applying, run VACUUM ANALYZE products; so the planner has fresh
--     stats on the new column.
-- ============================================================================

-- Enable unaccent dictionary (bundled with Postgres but not active by default).
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Build a Vietnamese-friendly text search configuration.
-- COPY simple: tokenization, lowercasing are inherited from 'simple'.
-- ALTER MAPPING: route words through unaccent first, then simple (idempotent pass).
CREATE TEXT SEARCH CONFIGURATION vn_simple ( COPY = simple );
ALTER TEXT SEARCH CONFIGURATION vn_simple
    ALTER MAPPING FOR hword, hword_part, word WITH unaccent, simple;

-- Generated tsvector column. Postgres recomputes it automatically on
-- INSERT/UPDATE of name/sku/description. We do NOT expose this column to JPA;
-- Hibernate's ddl-auto=validate only checks declared fields, and Hibernate
-- cannot write into a GENERATED column anyway.
ALTER TABLE products
    ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('vn_simple', coalesce(name, '')), 'A') ||
        setweight(to_tsvector('vn_simple', coalesce(sku, '')), 'B') ||
        setweight(to_tsvector('vn_simple', coalesce(description, '')), 'C')
    ) STORED;

-- GIN index supports @@ (tsvector @@ tsquery) lookups and is the standard
-- index for full-text search. For 1M rows with average description ~200 chars,
-- this index will be approximately 500MB-1GB on disk.
--
-- Note: GIN index build is INSERT/DELETE-friendly but UPDATE-expensive (the
-- whole tsvector has to be re-indexed on each UPDATE). For our usage pattern
-- (product catalog, infrequent edits), this trade-off is acceptable.
CREATE INDEX idx_products_search_vector
    ON products USING GIN (search_vector);