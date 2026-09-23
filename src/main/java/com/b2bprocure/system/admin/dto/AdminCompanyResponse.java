package com.b2bprocure.system.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Step 8 — Admin Company Detail response.
 *
 * Extends the standard company fields with aggregate counts
 * that are relevant for admin management decisions.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminCompanyResponse {

    private Long id;
    private String name;
    private String taxCode;
    private String email;
    private String phone;
    private String address;
    private String companyType;   // BUYER or SUPPLIER
    private String status;       // ACTIVE, INACTIVE, BLOCKED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Aggregate counts for admin visibility
    private long userCount;
    private long productCount;
}
