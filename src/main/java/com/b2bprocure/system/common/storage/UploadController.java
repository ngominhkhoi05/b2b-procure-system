package com.b2bprocure.system.common.storage;

import com.b2bprocure.system.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Generic authenticated upload endpoint.
 *
 * The frontend uses this to send image files to the backend, which in
 * turn uploads to Cloudinary and returns a stable secure URL. That URL
 * is then attached to the relevant business entity via the existing
 * JSON endpoints (PUT /api/v1/users/me, PUT /api/v1/products/{id}, ...).
 *
 * Why a separate endpoint instead of changing the user/product APIs to
 * accept multipart/form-data?
 *   - The existing JSON APIs are reused by admin tools, future mobile
 *     clients, integrations, etc. Switching them all to multipart is a
 *     huge blast radius.
 *   - This endpoint centralises file validation + Cloudinary error
 *     handling in one place.
 *   - Frontend still has a single source of truth for "given a file,
 *     give me a URL".
 *
 * Authorization: any authenticated user can upload. Cloudinary folder
 * names enforce the logical separation (avatars / covers / products).
 */
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
@Tag(name = "Upload", description = "Generic authenticated upload endpoints")
public class UploadController {

    private final CloudinaryStorageService storage;

    /**
     * Upload an avatar image. Folder: {@code <root>/avatars}.
     */
    @Operation(
        summary = "Upload avatar image",
        description = "Upload an image file for use as a user avatar. Returns a Cloudinary secure URL."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Image uploaded successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file (empty, wrong type, too large)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UploadResponse>> uploadAvatar(
            @RequestPart("file") MultipartFile file) {
        UploadResult result = storage.uploadImage(file, "avatars");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Avatar uploaded successfully", toResponse(result)));
    }

    /**
     * Upload a cover image. Folder: {@code <root>/covers}.
     */
    @Operation(
        summary = "Upload cover image",
        description = "Upload an image file for use as a profile cover. Returns a Cloudinary secure URL."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Image uploaded successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file (empty, wrong type, too large)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(value = "/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UploadResponse>> uploadCover(
            @RequestPart("file") MultipartFile file) {
        UploadResult result = storage.uploadImage(file, "covers");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Cover image uploaded successfully", toResponse(result)));
    }

    /**
     * Upload a product image. Folder: {@code <root>/products}.
     *
     * Access is restricted to ADMIN and SUPPLIER — Buyers have no business
     * uploading product imagery. (Rule 28: authorization enforced server-side.)
     */
    @Operation(
        summary = "Upload product image",
        description = "Upload an image file for use as a product image. Admin or Supplier only."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Image uploaded successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file (empty, wrong type, too large)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — Supplier or Admin only",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(value = "/product", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPLIER')")
    public ResponseEntity<ApiResponse<UploadResponse>> uploadProductImage(
            @RequestPart("file") MultipartFile file,
            // Optional category folder override. Kept simple: a single
            // "folder" param. The frontend may pass "products/<productId>"
            // once the product exists, or just "products" before that.
            @RequestParam(value = "folder", required = false, defaultValue = "products") String folder) {
        UploadResult result = storage.uploadImage(file, folder);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product image uploaded successfully", toResponse(result)));
    }

    // ── Mapper ──────────────────────────────────────────────────────────────

    private UploadResponse toResponse(UploadResult r) {
        return new UploadResponse(r.publicId(), r.url(), r.format(), r.width(), r.height(), r.sizeBytes());
    }

    /**
     * Public DTO returned to the frontend. Kept as a record so future
     * fields (e.g. an ETag, deletion token) can be added without touching
     * every controller method.
     */
    public record UploadResponse(
            String publicId,
            String url,
            String format,
            Integer width,
            Integer height,
            long sizeBytes
    ) { }
}
