package com.b2bprocure.system.user.service;

import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.common.exception.ResourceNotFoundException;
import com.b2bprocure.system.common.response.PageResponse;
import com.b2bprocure.system.common.util.SecurityUtil;
import com.b2bprocure.system.user.dto.ChangePasswordRequest;
import com.b2bprocure.system.user.dto.UpdateUserRequest;
import com.b2bprocure.system.user.dto.UserResponse;
import com.b2bprocure.system.user.dto.UserStatusUpdateRequest;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.mapper.UserMapper;
import com.b2bprocure.system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    private User getCurrentAuthenticatedUser() {
        Optional<Long> userIdOpt = SecurityUtil.getCurrentUserId();
        if (userIdOpt.isPresent()) {
            return userRepository.findByIdWithRoleAndCompany(userIdOpt.get())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userIdOpt.get()));
        }
        String username = SecurityUtil.getCurrentUsernameOrThrow();
        return userRepository.findByUsernameWithRoleAndCompany(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        User currentUser = getCurrentAuthenticatedUser();
        return userMapper.toResponse(currentUser);
    }

    @Override
    @Transactional
    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        User currentUser = getCurrentAuthenticatedUser();
        userMapper.updateEntity(request, currentUser);
        currentUser.setUpdatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(currentUser);
        log.info("User id: {} updated their profile", currentUser.getId());
        return userMapper.toResponse(savedUser);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User currentUser = getCurrentAuthenticatedUser();

        if (currentUser.getPassword() == null) {
            log.warn("User id: {} attempted password change but has no password set (OAuth2 account)", currentUser.getId());
            throw new BusinessException("Account registered via OAuth2 does not have a password set. Password change is not supported.", HttpStatus.BAD_REQUEST);
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), currentUser.getPassword())) {
            log.warn("User id: {} provided incorrect current password", currentUser.getId());
            throw new BusinessException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        }

        if (passwordEncoder.matches(request.getNewPassword(), currentUser.getPassword())) {
            throw new BusinessException("New password must be different from current password", HttpStatus.BAD_REQUEST);
        }

        currentUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        currentUser.setUpdatedAt(LocalDateTime.now());
        userRepository.save(currentUser);       
        log.info("User id: {} successfully changed their password", currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(String role, String status, String keyword, Pageable pageable) {
        String normalizedRole = (role != null && !role.isBlank()) ? role.trim().toUpperCase() : null;
        String normalizedStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;
        String pattern = (keyword != null && !keyword.isBlank()) ? "%" + keyword.trim().toLowerCase() + "%" : null;

        Page<User> userPage = userRepository.searchUsers(normalizedRole, normalizedStatus, pattern, pageable);
        List<UserResponse> content = userPage.getContent().stream()
                .map(userMapper::toResponse)
                .toList();

        return PageResponse.of(userPage, content);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findByIdWithRoleAndCompany(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUserByAdmin(Long id, UpdateUserRequest request) {
        User user = userRepository.findByIdWithRoleAndCompany(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        userMapper.updateEntity(request, user);
        user.setUpdatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);
        log.info("Admin updated profile for user id: {}", id);
        return userMapper.toResponse(savedUser);
    }

    @Override
    @Transactional
    public UserResponse updateUserStatus(Long id, UserStatusUpdateRequest request) {
        User user = userRepository.findByIdWithRoleAndCompany(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new BusinessException("Status is required", HttpStatus.BAD_REQUEST);
        }

        String normalizedStatus = request.getStatus().trim().toUpperCase();
        if (!"ACTIVE".equals(normalizedStatus) && !"INACTIVE".equals(normalizedStatus) && !"BLOCKED".equals(normalizedStatus)) {
            throw new BusinessException("Invalid user status: " + request.getStatus() + ". Allowed statuses: ACTIVE, INACTIVE, BLOCKED", HttpStatus.BAD_REQUEST);
        }

        User currentUser = getCurrentAuthenticatedUser();
        if (currentUser.getId().equals(user.getId()) && !"ACTIVE".equals(normalizedStatus)) {
            throw new BusinessException("Admin cannot deactivate or block their own account", HttpStatus.BAD_REQUEST);
        }

        user.setStatus(normalizedStatus);
        user.setUpdatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);
        log.info("Admin updated status of user id: {} to {}", id, normalizedStatus);
        return userMapper.toResponse(savedUser);
    }

}
