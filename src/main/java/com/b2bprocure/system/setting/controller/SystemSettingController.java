package com.b2bprocure.system.setting.controller;

import com.b2bprocure.system.common.response.ApiResponse;
import com.b2bprocure.system.common.util.SecurityUtil;
import com.b2bprocure.system.setting.dto.SystemSettingResponse;
import com.b2bprocure.system.setting.dto.UpdateSystemSettingRequest;
import com.b2bprocure.system.setting.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "System Setting", description = "System Settings Management APIs (Admin Only)")
@PreAuthorize("hasRole('ADMIN')")
public class SystemSettingController {

    private final SystemSettingService systemSettingService;

    @Operation(summary = "Get All System Settings", description = "Retrieve all system settings. Admin only.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Settings retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Admin access required",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<SystemSettingResponse>>> getAllSettings() {
        List<SystemSettingResponse> response = systemSettingService.getAllSettings();
        return ResponseEntity.ok(ApiResponse.success("Settings retrieved successfully", response));
    }

    @Operation(summary = "Get System Setting by Key", description = "Retrieve a specific system setting by key. Admin only.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Setting retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Admin access required",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Setting not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<SystemSettingResponse>> getSettingByKey(@PathVariable String key) {
        SystemSettingResponse response = systemSettingService.getSettingByKey(key);
        return ResponseEntity.ok(ApiResponse.success("Setting retrieved successfully", response));
    }

    @Operation(summary = "Update System Setting Value", description = "Update setting value of an existing setting key. Admin only.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Setting updated successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid setting value",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Admin access required",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Setting not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<SystemSettingResponse>> updateSetting(
            @PathVariable String key,
            @Valid @RequestBody UpdateSystemSettingRequest request
    ) {
        Long currentUserId = SecurityUtil.getCurrentUserIdOrThrow();
        SystemSettingResponse response = systemSettingService.updateSetting(key, request, currentUserId);
        return ResponseEntity.ok(ApiResponse.success("Setting updated successfully", response));
    }

}
