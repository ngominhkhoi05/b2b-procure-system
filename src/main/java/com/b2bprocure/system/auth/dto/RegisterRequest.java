package com.b2bprocure.system.auth.dto;

import com.b2bprocure.system.company.dto.CreateCompanyRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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
@Schema(description = "Request DTO for traditional username/password registration")
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Schema(description = "Unique username", example = "john_buyer", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    @Schema(description = "User password", example = "password123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank(message = "Full name is required")
    @Schema(description = "Full name of the user", example = "John Doe", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "User email address", example = "john.doe@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "Phone number", example = "0912345678")
    private String phone;

    @Schema(description = "Company type: BUYER or SUPPLIER (optional if companyId is provided)", example = "BUYER")
    private String companyType;

    @Schema(description = "Existing company ID (optional, provide either companyId or company details)", example = "1")
    private Long companyId;

    @Valid
    @Schema(description = "New company details (optional, provide either companyId or company details)")
    private CreateCompanyRequest company;

}
