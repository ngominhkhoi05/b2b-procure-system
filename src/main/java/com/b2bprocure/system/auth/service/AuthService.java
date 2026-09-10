package com.b2bprocure.system.auth.service;

import com.b2bprocure.system.auth.dto.LoginRequest;
import com.b2bprocure.system.auth.dto.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

}
