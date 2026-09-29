package com.b2bprocure.system.user.service;

import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.common.exception.ResourceNotFoundException;
import com.b2bprocure.system.common.response.PageResponse;
import com.b2bprocure.system.common.storage.CloudinaryStorageService;
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
    private final CloudinaryStorageService cloudinaryStorage;

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

        // MapStruct fills the simple fields (fullName, phone, avatarUrl,
        // coverImageUrl) with null=ignore semantics. Image publicIds are
        // intentionally excluded from the mapper and handled below because
        // they need to coordinate with a Cloudinary destroy call.
        userMapper.updateEntity(request, currentUser);

        // Handle avatar replace / clear with Cloudinary cleanup.
        replaceUserImage(
            currentUser,
            /* newUrl      */ request.getAvatarUrl(),
            /* newPublicId */ request.getAvatarPublicId(),
            /* oldUrl      */ currentUser.getAvatarUrl(),
            /* oldPublicId */ currentUser.getAvatarPublicId(),
            /* isAvatar    */ true);

        // Handle cover replace / clear with Cloudinary cleanup.
        replaceUserImage(
            currentUser,
            /* newUrl      */ request.getCoverImageUrl(),
            /* newPublicId */ request.getCoverImagePublicId(),
            /* oldUrl      */ currentUser.getCoverImageUrl(),
            /* oldPublicId */ currentUser.getCoverImagePublicId(),
            /* isAvatar    */ false);

        currentUser.setUpdatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(currentUser);
        log.info("User id: {} updated their profile", currentUser.getId());
        return userMapper.toResponse(savedUser);
    }

    /**
     * Apply a single image replace/clear decision for the current user.
     *
     * Decision matrix:
     *   newUrl blank/empty → user wants to clear the image → destroy old, set null.
     *   newUrl == oldUrl   → no-op, do not destroy (could be a save with no change).
     *   newUrl != oldUrl   → user uploaded a new image → destroy old, save new + publicId.
     *
     * Why is the destroy call inside the @Transactional method but its failure
     * is only logged?
     *   Losing the old Cloudinary file is annoying but never blocks the user
     *   from saving a new avatar. Rolling back the whole profile save because
     *   the delete call failed would punish the user for our housekeeping
     *   problem. A separate cron (future work) can sweep orphans.
     */
    private void replaceUserImage(
        User user,
        String newUrl,
        String newPublicId,
        String oldUrl,
        String oldPublicId,
        boolean isAvatar
    ) {
        String kind = isAvatar ? "avatar" : "cover";
        boolean clearing = (newUrl == null || newUrl.isBlank());
        boolean changing = !clearing && !newUrl.equals(oldUrl);

        if (!clearing && !changing) {
            return; // URL unchanged — nothing to do (also no destroy).
        }

        // Best-effort cleanup of the previous Cloudinary asset. We only
        // destroy when there is actually a previous publicId to clean up.
        if (oldPublicId != null && !oldPublicId.isBlank()) {
            try {
                cloudinaryStorage.delete(oldPublicId);
            } catch (Exception ex) {
                // CloudinaryStorageService.delete already swallows + logs
                // non-fatal failures (per its contract: "failures are
                // logged but never thrown"). This catch is a defensive
                // belt-and-braces in case that contract ever changes.
                log.warn("Failed to delete old {} image (publicId={}): {}",
                    kind, oldPublicId, ex.getMessage());
            }
        }

        if (clearing) {
            user.setAvatarUrl(null);
            user.setAvatarPublicId(null);
        } else {
            user.setAvatarUrl(newUrl);
            user.setAvatarPublicId(
                newPublicId != null && !newPublicId.isBlank() ? newPublicId : null);
        }
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

        // Snapshot old image references BEFORE the mapper overwrites them.
        String oldAvatarUrl      = user.getAvatarUrl();
        String oldAvatarPublicId = user.getAvatarPublicId();
        String oldCoverUrl       = user.getCoverImageUrl();
        String oldCoverPublicId  = user.getCoverImagePublicId();

        userMapper.updateEntity(request, user);

        // Admins can also trigger avatar/cover replacement. Reuse the same
        // helper so the cleanup policy is identical to self-service updates.
        replaceUserImage(user,
            request.getAvatarUrl(), request.getAvatarPublicId(),
            oldAvatarUrl, oldAvatarPublicId, true);
        replaceUserImage(user,
            request.getCoverImageUrl(), request.getCoverImagePublicId(),
            oldCoverUrl, oldCoverPublicId, false);

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
