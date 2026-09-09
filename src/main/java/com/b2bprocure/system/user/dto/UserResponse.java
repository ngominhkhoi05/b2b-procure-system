package com.b2bprocure.system.user.dto;

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
public class UserResponse {

    private Long id;
    private Long roleId;
    private String roleName;
    private Long companyId;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private String coverImageUrl;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
