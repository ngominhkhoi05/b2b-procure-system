package com.b2bprocure.system.commission.mapper;

import com.b2bprocure.system.admin.dto.CreateCommissionRateRequest;
import com.b2bprocure.system.commission.entity.CommissionRate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for {@link #as well as responses}.
 * Centralised here so the admin/components share a single mapping source of truth.
 *
 * Why some fields are populated by the Service instead of the Mapper:
 *   - {@code createdBy} is a {@code @ManyToOne(LAZY)} association. MapStruct
 *     MUST NOT trigger an extra repository load (AGENTS.md Rule 22).
 *   - The Service is responsible for resolving the {@code createdBy} entity
 *     (e.g. via {@code userRepository.findByIdWithRoleAndCompany}) and calling
 *     on the entity before passing it to the mapper. See
 *     {@link com.b2bprocure.system.admin.service.AdminCommissionRateServiceImpl}.
 *   - Similarly, {@code createdAt} is set in code (LocalDateTime.now()) so the
 *     Mapper only needs to skip it.
 */
@Mapper(componentModel = "spring")
public interface CommissionRateMapper {

    /**
     * Map a {@link #entity} to its admin response. The caller MUST have
     * initialised {@code rate.getCreatedBy()} (for example via a fetch-join
     * or by setting it in code) so the lazy fields can be read in the same session.
     */
    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "createdByFullName", source = "createdBy.fullName")
    com.b2bprocure.system.admin.dto.CommissionRateResponse toResponse(CommissionRate rate);

    /**
     * Map a create request to a new entity. Audit fields ({@code id},
     * {@code createdAt}, {@code createdBy}) are intentionally ignored.
     * The Service is responsible for assigning them after mapping.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    CommissionRate toEntity(CreateCommissionRateRequest request);
}