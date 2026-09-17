package com.b2bprocure.system.setting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSettingResponse {

    private Long id;
    private String settingKey;
    private String settingValue;
    private String description;
    private LocalDateTime updatedAt;
    private Long updatedBy;

}
