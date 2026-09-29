package com.b2bprocure.system.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request DTO for updating user profile")
public class UpdateUserRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must not exceed 100 characters")
    @Schema(description = "User full name", example = "John Doe", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fullName;

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    @Schema(description = "Phone number", example = "0912345678")
    private String phone;

    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    @Schema(description = "Avatar image URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    @Size(max = 500, message = "Cover image URL must not exceed 500 characters")
    @Schema(description = "Cover image URL", example = "https://example.com/cover.jpg")
    private String coverImageUrl;

    @Size(max = 255, message = "Avatar publicId must not exceed 255 characters")
    @Schema(description = "Cloudinary publicId for avatar, returned by /api/v1/uploads/avatar. Used so the service can delete the old file on replace.",
            example = "b2b-procure/dev/users/avatars/abc123")
    private String avatarPublicId;

    @Size(max = 255, message = "Cover image publicId must not exceed 255 characters")
    @Schema(description = "Cloudinary publicId for cover image, returned by /api/v1/uploads/cover. Used so the service can delete the old file on replace.",
            example = "b2b-procure/dev/users/covers/abc123")
    private String coverImagePublicId;

}
