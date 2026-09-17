package com.b2bprocure.system.setting.service;

import com.b2bprocure.system.common.enums.SettingKey;
import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.common.exception.ResourceNotFoundException;
import com.b2bprocure.system.common.util.SecurityUtil;
import com.b2bprocure.system.setting.dto.SystemSettingResponse;
import com.b2bprocure.system.setting.dto.UpdateSystemSettingRequest;
import com.b2bprocure.system.setting.entity.SystemSetting;
import com.b2bprocure.system.setting.mapper.SystemSettingMapper;
import com.b2bprocure.system.setting.repository.SystemSettingRepository;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingServiceImpl implements SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;
    private final SystemSettingMapper systemSettingMapper;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SystemSettingResponse> getAllSettings() {
        if (!SecurityUtil.isAdmin()) {
            throw new AccessDeniedException("Access denied: Only administrators can access system settings");
        }
        List<SystemSetting> settings = systemSettingRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        return systemSettingMapper.toResponseList(settings);
    }

    @Override
    @Transactional(readOnly = true)
    public SystemSettingResponse getSettingByKey(String key) {
        if (!SecurityUtil.isAdmin()) {
            throw new AccessDeniedException("Access denied: Only administrators can access system settings");
        }
        if (key == null || key.isBlank()) {
            throw new ResourceNotFoundException("SystemSetting", "settingKey", key);
        }
        SystemSetting setting = systemSettingRepository.findBySettingKey(key.trim())
                .orElseThrow(() -> new ResourceNotFoundException("SystemSetting", "settingKey", key));
        return systemSettingMapper.toResponse(setting);
    }

    @Override
    @Transactional
    public SystemSettingResponse updateSetting(String key, UpdateSystemSettingRequest request, Long updatedBy) {
        if (!SecurityUtil.isAdmin()) {
            throw new AccessDeniedException("Access denied: Only administrators can update system settings");
        }
        if (key == null || key.isBlank()) {
            throw new ResourceNotFoundException("SystemSetting", "settingKey", key);
        }
        SystemSetting setting = systemSettingRepository.findBySettingKey(key.trim())
                .orElseThrow(() -> new ResourceNotFoundException("SystemSetting", "settingKey", key));

        String valueToValidate = (request != null) ? request.getSettingValue() : null;
        validateSettingValue(setting.getSettingKey(), valueToValidate);

        User adminUser = userRepository.findById(updatedBy)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", updatedBy));

        setting.setSettingValue(request.getSettingValue().trim());
        setting.setUpdatedAt(LocalDateTime.now());
        setting.setUpdatedBy(adminUser);

        SystemSetting savedSetting = systemSettingRepository.save(setting);
        log.info("System setting '{}' updated to '{}' by user ID {}", setting.getSettingKey(), setting.getSettingValue(), updatedBy);
        return systemSettingMapper.toResponse(savedSetting);
    }

    @Override
    @Transactional(readOnly = true)
    public String getSettingValue(SettingKey key) {
        if (key == null) {
            return null;
        }
        return systemSettingRepository.findBySettingKey(key.name())
                .map(SystemSetting::getSettingValue)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public int getSettingValueAsInt(SettingKey key, int defaultValue) {
        String value = getSettingValue(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer in database for setting key {}: '{}'", key, value);
            return defaultValue;
        }
    }

    private void validateSettingValue(String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException("Setting value must not be blank", HttpStatus.BAD_REQUEST);
        }
        String trimmed = value.trim();

        if (SettingKey.PAYMENT_TIMEOUT_MINUTES.name().equals(key)
                || SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS.name().equals(key)) {
            int intVal;
            try {
                intVal = Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                throw new BusinessException("Setting value must be a valid integer: " + trimmed, HttpStatus.BAD_REQUEST);
            }

            if (intVal <= 0) {
                throw new BusinessException("Setting value must be a positive integer greater than 0: " + trimmed, HttpStatus.BAD_REQUEST);
            }
        }
    }

}
