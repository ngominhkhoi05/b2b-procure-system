package com.b2bprocure.system.company.dto;

import jakarta.validation.constraints.Email;
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
public class UpdateCompanyRequest {

    @NotBlank(message = "Company name is required")
    private String name;

    private String taxCode;

    @Email(message = "Invalid email format")
    private String email;

    private String phone;

    private String address;
}
