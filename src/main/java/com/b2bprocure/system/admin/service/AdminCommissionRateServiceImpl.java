package com.b2bprocure.system.admin.service;

import com.b2bprocure.system.admin.dto.CommissionRateResponse;
import com.b2bprocure.system.admin.dto.CreateCommissionRateRequest;
import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.common.exception.ResourceNotFoundException;
import com.b2bprocure.system.common.response.PageResponse;
import com.b2bprocure.system.common.util.SecurityUtil;
import com.b2bprocure.system.commission.entity.CommissionRate;
import com.b2bprocure.system.commission.mapper.CommissionRateMapper;
import com.b2bprocure.system.commission.repository.CommissionRateRepository;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin-facing commission rate service. Delegates mapping to
 * {@link CommissionRateMapper} so DTO ↔ Entity conversion lives in one place.
 *
 * Audit fields ({@code createdAt}, {@code createdBy}) are populated by the
 * Service — MapStruct is intentionally kept free of repository/session
 * concerns (AGENTS.md Rule 22).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCommissionRateServiceImpl implements AdminCommissionRateService {

    private final CommissionRateRepository commissionRateRepository;
    private final UserRepository userRepository;
    private final CommissionRateMapper commissionRateMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommissionRateResponse> listRates(Pageable pageable) {
        if (!SecurityUtil.isAdmin()) {
            throw new AccessDeniedException("Access denied: Only administrators can list commission rates via this endpoint");
        }

        // The repository provides findAllByOrderByEffectiveFromDesc — historical ordering
        // is preserved as specified.
        Page<CommissionRate> page = commissionRateRepository.findAllByOrderByEffectiveFromDesc(pageable);

        List<CommissionRateResponse> content = page.getContent().stream()
                .map(commissionRateMapper::toResponse)
                .toList();

        return PageResponse.of(page, content);
    }

    @Override
    @Transactional
    public CommissionRateResponse createRate(CreateCommissionRateRequest request) {
        if (!SecurityUtil.isAdmin()) {
            throw new AccessDeniedException("Access denied: Only administrators can create commission rates");
        }

        if (request.getRate() == null) {
            throw new BusinessException("Rate is required", HttpStatus.BAD_REQUEST);
        }
        if (request.getEffectiveFrom() == null) {
            throw new BusinessException("effectiveFrom is required", HttpStatus.BAD_REQUEST);
        }

        // Negative rates are explicitly rejected by validation, but be defensive too.
        if (request.getRate().signum() < 0) {
            throw new BusinessException(
                    "Rate must be >= 0. Submitted: " + request.getRate(),
                    HttpStatus.BAD_REQUEST
            );
        }

        Long currentUserId = SecurityUtil.getCurrentUserIdOrThrow();
        User currentUser = userRepository.findByIdWithRoleAndCompany(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        // MapStruct handles the request → entity conversion for primitive fields.
        // Audit fields are assigned by the Service because MapStruct is not allowed
        // to query the repository (AGENTS.md Rule 22).
        CommissionRate rate = commissionRateMapper.toEntity(request);
        rate.setCreatedAt(LocalDateTime.now());
        rate.setCreatedBy(currentUser);

        CommissionRate saved = commissionRateRepository.save(rate);
        log.info("Commission rate created: id={}, rate={}, effectiveFrom={}, createdBy={}",
                saved.getId(), saved.getRate(), saved.getEffectiveFrom(), currentUser.getUsername());

        return commissionRateMapper.toResponse(saved);
    }
}