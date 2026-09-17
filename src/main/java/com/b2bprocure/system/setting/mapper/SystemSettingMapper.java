package com.b2bprocure.system.setting.mapper;

import com.b2bprocure.system.setting.dto.SystemSettingResponse;
import com.b2bprocure.system.setting.entity.SystemSetting;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SystemSettingMapper {

    @Mapping(target = "updatedBy", source = "updatedBy.id")
    SystemSettingResponse toResponse(SystemSetting systemSetting);

    List<SystemSettingResponse> toResponseList(List<SystemSetting> systemSettings);

}
