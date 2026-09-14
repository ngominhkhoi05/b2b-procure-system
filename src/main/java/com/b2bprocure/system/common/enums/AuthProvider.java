package com.b2bprocure.system.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthProvider {

    GOOGLE("Google OAuth2"),
    GITHUB("GitHub OAuth2"),
    FACEBOOK("Facebook OAuth2");

    private final String description;

}
