package com.b2bprocure.system.setting.service;

import com.b2bprocure.system.common.enums.SettingKey;
import com.b2bprocure.system.setting.dto.SystemSettingResponse;
import com.b2bprocure.system.setting.dto.UpdateSystemSettingRequest;

import java.util.List;

public interface SystemSettingService {

    /**
     * Retrieve all system settings (Admin only).
     */
    List<SystemSettingResponse> getAllSettings();

    /**
     * Retrieve a system setting by its key (Admin only).
     */
    SystemSettingResponse getSettingByKey(String key);

    /**
     * Update the value of an existing system setting (Admin only).
     */
    SystemSettingResponse updateSetting(String key, UpdateSystemSettingRequest request, Long updatedBy);

    /**
     * Internal lookup: Get raw setting string value by predefined SettingKey.
     */
    String getSettingValue(SettingKey key);

    /**
     * Internal lookup: Get setting value as integer with fallback default value.
     */
    int getSettingValueAsInt(SettingKey key, int defaultValue);

}
