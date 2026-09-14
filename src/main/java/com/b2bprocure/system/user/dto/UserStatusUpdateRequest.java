package com.b2bprocure.system.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Request DTO for updating user status")
public class UserStatusUpdateRequest {

    @NotBlank(message = "Status is required")
    @Schema(description = "User status (ACTIVE, INACTIVE, BLOCKED)", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

}
