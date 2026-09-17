package com.b2bprocure.system.setting.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSystemSettingRequest {

    @NotBlank(message = "Setting value must not be blank")
    private String settingValue;

}
