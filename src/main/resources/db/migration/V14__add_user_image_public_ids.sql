-- ============================================================================
-- V14__add_user_image_public_ids.sql
-- B2B Procure System - Track Cloudinary publicId for user avatar & cover
--
-- Reason:
--   When a user replaces their avatar or cover image, the old file on
--   Cloudinary is currently orphaned (the URL is overwritten in the DB
--   but nothing deletes the previous Cloudinary asset). That accumulates
--   as storage garbage over time.
--
--   We store the Cloudinary publicId alongside the URL so the user
--   service can call cloudinary.uploader().destroy(publicId) before
--   writing the new URL. See CloudinaryStorageService.destroy(...).
--
--   Length 255 matches Cloudinary's own public_id column width guidance
--   and is plenty for any folder/timestamp/filename combination we
--   generate (e.g. "b2b-procure/dev/users/avatars/abc123def").
--
-- Safety:
--   - Additive only (two nullable VARCHAR columns). No data migration
--     needed — existing rows simply have NULL publicId until the next
--     upload, at which point we delete the previous file opportunistically
--     (and the previous file's publicId is lost anyway, so we accept that
--     pre-V14 images are unrecoverable for deletion).
--   - No index needed: lookup is always by user id (PK lookup), never
--     by publicId.
-- ============================================================================

ALTER TABLE users
    ADD COLUMN avatar_public_id       VARCHAR(255),
    ADD COLUMN cover_image_public_id VARCHAR(255);
