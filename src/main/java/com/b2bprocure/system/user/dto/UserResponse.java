package com.b2bprocure.system.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response DTO representing user profile")
public class UserResponse {

    @Schema(description = "User unique ID", example = "1")
    private Long id;

    @Schema(description = "Role ID", example = "2")
    private Long roleId;

    @Schema(description = "Role name", example = "BUYER")
    private String roleName;

    @Schema(description = "Associated company ID (null for system admin)", example = "1")
    private Long companyId;

    @Schema(description = "Associated company name", example = "B2B Retail Corporation")
    private String companyName;

    @Schema(description = "Username", example = "buyer")
    private String username;

    @Schema(description = "Full name", example = "Buyer Manager")
    private String fullName;

    @Schema(description = "Email address", example = "buyer@gmail.com")
    private String email;

    @Schema(description = "Phone number", example = "0900000002")
    private String phone;

    @Schema(description = "Avatar URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    @Schema(description = "Cover image URL", example = "https://example.com/cover.jpg")
    private String coverImageUrl;

    @Schema(description = "Account status (ACTIVE, INACTIVE, BLOCKED)", example = "ACTIVE")
    private String status;

    @Schema(description = "Account creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;

}
