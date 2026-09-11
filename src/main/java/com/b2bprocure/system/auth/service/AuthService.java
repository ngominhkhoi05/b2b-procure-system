package com.b2bprocure.system.auth.service;

import com.b2bprocure.system.auth.dto.LoginRequest;
import com.b2bprocure.system.auth.dto.LoginResponse;
import com.b2bprocure.system.auth.dto.OAuth2RegisterRequest;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse registerOAuth2(OAuth2RegisterRequest request, String registrationToken);

}
