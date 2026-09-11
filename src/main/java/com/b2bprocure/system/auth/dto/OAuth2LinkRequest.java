package com.b2bprocure.system.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Request DTO for linking Google OAuth2 account to the current user")
public class OAuth2LinkRequest {

    @Schema(description = "Optional temporary OAuth2 link token obtained from the OAuth2 callback (if not relying on server-side state)")
    private String linkToken;

}
