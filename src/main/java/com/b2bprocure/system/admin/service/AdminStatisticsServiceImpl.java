package com.b2bprocure.system.admin.service;

import com.b2bprocure.system.admin.dto.AdminStatisticsResponse;
import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.common.util.SecurityUtil;
import com.b2bprocure.system.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Step 8 — Admin Statistics overview implementation.
 *
 * Uses existing order columns (subtotal, commission_amount, status). NO re-computation
 * of commission — only the values snapshotted on each Order are summed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStatisticsServiceImpl implements AdminStatisticsService {

    private final OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminStatisticsResponse getOverview(LocalDate fromDate, LocalDate toDate) {
        if (!SecurityUtil.isAdmin()) {
            throw new AccessDeniedException("Access denied: Only administrators can view statistics via this endpoint");
        }

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException("fromDate must be on or before toDate", HttpStatus.BAD_REQUEST);
        }

        // Inclusive start, exclusive next-day start end (matches Step 7 date convention).
        LocalDateTime fromDateTime = (fromDate != null) ? fromDate.atStartOfDay() : null;
        LocalDateTime toDateExclusive = (toDate != null) ? toDate.plusDays(1).atStartOfDay() : null;

        long total = orderRepository.countByStatusAndDateRange(null, fromDateTime, toDateExclusive);
        long completed = orderRepository.countByStatusAndDateRange(OrderStatus.COMPLETED, fromDateTime, toDateExclusive);
        long pending = orderRepository.countByStatusAndDateRange(OrderStatus.PENDING_CONFIRMATION, fromDateTime, toDateExclusive);
        long cancelled = orderRepository.countByStatusAndDateRange(OrderStatus.CANCELLED, fromDateTime, toDateExclusive);
        long rejected = orderRepository.countByStatusAndDateRange(OrderStatus.REJECTED, fromDateTime, toDateExclusive);

        // Financial metrics: only COMPLETED orders have meaningful commission snapshots.
        Object[] row = orderRepository.sumFinancialsByStatusesAndDateRange(
                List.of(OrderStatus.COMPLETED), fromDateTime, toDateExclusive);

        BigDecimal totalOrderValue = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        if (row != null && row.length >= 2) {
            Object totalOrderRaw = row[0];
            Object totalCommissionRaw = row[1];
            if (totalOrderRaw instanceof BigDecimal bd) {
                totalOrderValue = bd;
            } else if (totalOrderRaw instanceof Number n) {
                totalOrderValue = BigDecimal.valueOf(n.doubleValue());
            }
            if (totalCommissionRaw instanceof BigDecimal bd) {
                totalCommission = bd;
            } else if (totalCommissionRaw instanceof Number n) {
                totalCommission = BigDecimal.valueOf(n.doubleValue());
            }
        }

        return AdminStatisticsResponse.builder()
                .totalOrders(total)
                .completedOrders(completed)
                .pendingConfirmationOrders(pending)
                .cancelledOrders(cancelled)
                .rejectedOrders(rejected)
                .totalOrderValue(totalOrderValue)
                .totalCommission(totalCommission)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();
    }
}
