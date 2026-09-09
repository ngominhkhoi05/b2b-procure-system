package com.b2bprocure.system.commission.mapper;

import com.b2bprocure.system.commission.dto.CommissionRateResponse;
import com.b2bprocure.system.commission.dto.CreateCommissionRateRequest;
import com.b2bprocure.system.commission.dto.UpdateCommissionRateRequest;
import com.b2bprocure.system.commission.entity.CommissionRate;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface CommissionRateMapper {

    @Mapping(target = "createdBy", source = "createdBy.id")
    CommissionRateResponse toResponse(CommissionRate commissionRate);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    CommissionRate toEntity(CreateCommissionRateRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(UpdateCommissionRateRequest request, @MappingTarget CommissionRate commissionRate);

}
