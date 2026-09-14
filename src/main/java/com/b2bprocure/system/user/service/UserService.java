package com.b2bprocure.system.user.service;

import com.b2bprocure.system.common.response.PageResponse;
import com.b2bprocure.system.user.dto.ChangePasswordRequest;
import com.b2bprocure.system.user.dto.UpdateUserRequest;
import com.b2bprocure.system.user.dto.UserResponse;
import com.b2bprocure.system.user.dto.UserStatusUpdateRequest;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserResponse getCurrentUser();

    UserResponse updateCurrentUser(UpdateUserRequest request);

    void changePassword(ChangePasswordRequest request);

    PageResponse<UserResponse> getUsers(String role, String status, String keyword, Pageable pageable);

    UserResponse getUserById(Long id);

    UserResponse updateUserByAdmin(Long id, UpdateUserRequest request);

    UserResponse updateUserStatus(Long id, UserStatusUpdateRequest request);

}
