package com.b2bprocure.system.admin.service;

import com.b2bprocure.system.admin.dto.CommissionRateResponse;
import com.b2bprocure.system.admin.dto.CreateCommissionRateRequest;
import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.common.exception.ResourceNotFoundException;
import com.b2bprocure.system.common.response.PageResponse;
import com.b2bprocure.system.common.util.SecurityUtil;
import com.b2bprocure.system.commission.entity.CommissionRate;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCommissionRateServiceImpl implements AdminCommissionRateService {

    private final CommissionRateRepository commissionRateRepository;
    private final UserRepository userRepository;

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
                .map(this::toResponse)
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

        CommissionRate rate = new CommissionRate();
        rate.setRate(request.getRate());
        rate.setEffectiveFrom(request.getEffectiveFrom());
        rate.setCreatedAt(LocalDateTime.now());
        rate.setCreatedBy(currentUser);

        CommissionRate saved = commissionRateRepository.save(rate);
        log.info("Commission rate created: id={}, rate={}, effectiveFrom={}, createdBy={}",
                saved.getId(), saved.getRate(), saved.getEffectiveFrom(), currentUser.getUsername());

        return toResponse(saved);
    }

    /**
     * Helper — maps an entity to the public response. Avoids MapStruct for a small DTO
     * to keep the layer simpler (does not need to fetch the lazy createdBy in a separate query
     * because the create path always touches {@code rate.getCreatedBy()} which is already loaded).
     */
    private CommissionRateResponse toResponse(CommissionRate rate) {
        return CommissionRateResponse.builder()
                .id(rate.getId())
                .rate(rate.getRate())
                .effectiveFrom(rate.getEffectiveFrom())
                .createdAt(rate.getCreatedAt())
                .createdById(rate.getCreatedBy() != null ? rate.getCreatedBy().getId() : null)
                .createdByFullName(rate.getCreatedBy() != null ? rate.getCreatedBy().getFullName() : null)
                .build();
    }
}
