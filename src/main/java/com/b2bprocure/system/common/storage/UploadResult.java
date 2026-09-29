package com.b2bprocure.system.common.storage;

/**
 * Outcome of a successful upload to Cloudinary.
 *
 * @param publicId   Cloudinary's stable identifier (e.g. "b2b-procure/avatars/abc123").
 *                   Persist this if you ever need to delete or transform the asset later.
 * @param url        Secure HTTPS URL — drop straight into <img src> or persist in DB.
 * @param format     File extension without the dot (e.g. "jpg", "png", "webp").
 * @param width      Image width in px. Null for non-image assets.
 * @param height     Image height in px. Null for non-image assets.
 * @param sizeBytes  Server-confirmed byte size, useful for billing/audit.
 */
public record UploadResult(
        String publicId,
        String url,
        String format,
        Integer width,
        Integer height,
        long sizeBytes
) { }
