package com.b2bprocure.system.common.storage;

import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.config.CloudinaryProperties;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Server-side image upload backed by Cloudinary.
 *
 * Design notes:
 *   - The service deliberately exposes narrow methods (uploadImage, delete)
 *     rather than a generic "upload(MultipartFile)" so call sites cannot
 *     accidentally upload arbitrary content types.
 *   - We do NOT use MapStruct for this — there's no entity ↔ DTO mapping,
 *     just bytes in / DTO out.
 *   - All Cloudinary credentials and limits come from {@link CloudinaryProperties},
 *     never from request input. (Rule 28: never trust client-supplied config.)
 *   - Allowed content types are whitelisted below to avoid surprise uploads
 *     (e.g. someone uploading an .exe renamed to .jpg).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif"
    );

    private final Cloudinary cloudinary;
    private final CloudinaryProperties props;

    /**
     * Upload an image to Cloudinary under {@code <rootFolder>/<subPath>}.
     *
     * @param file    multipart payload from the controller
     * @param subPath logical bucket, e.g. "avatars", "covers", "products/42"
     * @return UploadResult with publicId + secure URL
     * @throws BusinessException 400 if file is empty / wrong type / too large
     */
    public UploadResult uploadImage(MultipartFile file, String subPath) {
        validate(file);

        String folder = buildFolder(subPath);
        log.info("Uploading image to Cloudinary folder={} ({} bytes, type={})",
                folder, file.getSize(), file.getContentType());

        try {
            // Overwrite false, unique_filename true → Cloudinary de-duplicates
            // by hash, so re-uploading the same file does not waste storage.
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image",
                            "unique_filename", true,
                            "overwrite", false,
                            // Use auto to let Cloudinary pick the optimal delivery format
                            // (e.g. webp for browsers that support it).
                            "type", "upload"
                    )
            );

            return toResult(result);

        } catch (IOException e) {
            // Network / IO failures bubble up as 502 — the user's upload did
            // reach us, but we couldn't complete it. Retryable from the client.
            log.error("Cloudinary upload failed for folder={}: ", folder, e);
            throw new BusinessException(
                    "Image upload service is temporarily unavailable. Please try again.",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    /**
     * Delete a previously uploaded asset by its Cloudinary publicId.
     * Failures are logged but never thrown — deleting an already-gone asset
     * should not break the calling business flow.
     *
     * @param publicId value returned in {@link UploadResult#publicId()}, or null/blank to no-op
     */
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            String resultStatus = String.valueOf(result.get("result"));
            log.info("Cloudinary delete publicId={} status={}", publicId, resultStatus);
        } catch (IOException e) {
            // Log + swallow. The DB still holds the URL, but the asset will
            // eventually be garbage-collected by Cloudinary's retention policy.
            log.warn("Cloudinary delete failed for publicId={}: {}", publicId, e.getMessage());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File is empty", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > props.getMaxBytes()) {
            throw new BusinessException(
                    "File too large (max " + (props.getMaxBytes() / 1024 / 1024) + " MB)",
                    HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(
                    "Unsupported image type: " + contentType
                    + ". Allowed: jpeg, png, webp, gif",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private String buildFolder(String subPath) {
        String root = props.getFolder() == null ? "b2b-procure" : props.getFolder().trim();
        if (subPath == null || subPath.isBlank()) {
            return root;
        }
        // Strip leading/trailing slashes so concatenation is well-defined.
        String cleanSub = subPath.trim().replaceAll("^/+", "").replaceAll("/+$", "");
        return cleanSub.isEmpty() ? root : root + "/" + cleanSub;
    }

    private UploadResult toResult(Map<String, Object> result) {
        String publicId = (String) result.get("public_id");
        String url      = (String) result.get("secure_url");
        String format   = (String) result.get("format");

        Integer width  = toInt(result.get("width"));
        Integer height = toInt(result.get("height"));
        long bytes     = toLong(result.get("bytes"));

        if (publicId == null || url == null) {
            // Defensive: Cloudinary SDK shape changed before; never let null URLs leak out.
            log.error("Cloudinary returned an unexpected payload: {}", result);
            throw new BusinessException(
                    "Image upload service returned an invalid response",
                    HttpStatus.BAD_GATEWAY);
        }
        return new UploadResult(publicId, url, format, width, height, bytes);
    }

    private static Integer toInt(Object o) {
        return (o instanceof Number n) ? n.intValue() : null;
    }

    private static long toLong(Object o) {
        return (o instanceof Number n) ? n.longValue() : 0L;
    }
}
