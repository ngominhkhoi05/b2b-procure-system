package com.b2bprocure.system.auth.service;

import com.b2bprocure.system.auth.dto.LoginRequest;
import com.b2bprocure.system.auth.dto.LoginResponse;
import com.b2bprocure.system.auth.dto.OAuth2LinkRequest;
import com.b2bprocure.system.auth.dto.OAuth2RegisterRequest;
import com.b2bprocure.system.auth.dto.RegisterRequest;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse register(RegisterRequest request);

    LoginResponse registerOAuth2(OAuth2RegisterRequest request, String registrationToken);

    void linkOAuth2(Long currentUserId, OAuth2LinkRequest request);

}
