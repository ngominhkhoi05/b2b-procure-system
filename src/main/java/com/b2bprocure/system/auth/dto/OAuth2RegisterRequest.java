package com.b2bprocure.system.auth.dto;

import com.b2bprocure.system.company.dto.CreateCompanyRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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
@Schema(description = "Request DTO for Google OAuth2 first-time user registration")
public class OAuth2RegisterRequest {

    @NotBlank(message = "Company type is required")
    @Schema(description = "Company type: BUYER or SUPPLIER", example = "BUYER", requiredMode = Schema.RequiredMode.REQUIRED)
    private String companyType;

    @Schema(description = "Existing company ID (optional, provide either companyId or company details)", example = "1")
    private Long companyId;

    @Valid
    @Schema(description = "New company details (optional, provide either companyId or company details)")
    private CreateCompanyRequest company;

    @Schema(description = "Temporary OAuth2 registration token (optional if provided via Authorization header)")
    private String registrationToken;

}
