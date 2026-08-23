package com.smartfarm.service.controller;

import com.smartfarm.service.dto.DeviceListResponse;
import com.smartfarm.service.dto.DeviceRequest;
import com.smartfarm.service.dto.DeviceResponse;
import com.smartfarm.service.dto.DeviceSummaryResponse;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import com.smartfarm.service.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Device", description = "장비/센서 레지스트리 API")
@RestController
@RequestMapping("/api/farms/{farmId}/devices")
@RequiredArgsConstructor
@Validated
public class DeviceController {

    private final DeviceService deviceService;

    @Operation(summary = "장비 목록 조회 (멤버, kind·status·q(이름 부분일치)·zoneId 필터)")
    @GetMapping
    public ResponseEntity<DeviceListResponse> listDevices(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long farmId,
            @RequestParam(required = false) DeviceKind kind,
            @RequestParam(required = false) DeviceStatus status,
            // name 컬럼이 VARCHAR(50)이라 50 초과는 애초에 매칭 불가(리뷰 P3 #89) — 무제한 길이 입력 차단
            @RequestParam(required = false) @Size(max = 50, message = "검색어는 50자 이하여야 합니다.") String q,
            @RequestParam(required = false) Long zoneId) {
        return ResponseEntity.ok(deviceService.listDevices(farmId, userId, kind, status, q, zoneId));
    }

    @Operation(summary = "장비 KPI 요약 (멤버) — 총합·상태별·보정임박·제품군별 집계")
    @GetMapping("/summary")
    public ResponseEntity<DeviceSummaryResponse> summary(@AuthenticationPrincipal Long userId,
                                                          @PathVariable Long farmId) {
        return ResponseEntity.ok(deviceService.summary(farmId, userId));
    }

    @Operation(summary = "장비 등록 (OWNER, 데모 차단) — 위치(존·랙·층) 중 최소 하나 필수")
    @PostMapping
    public ResponseEntity<DeviceResponse> createDevice(@AuthenticationPrincipal Long userId,
                                                        @PathVariable Long farmId,
                                                        @Valid @RequestBody DeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceService.createDevice(farmId, userId, request));
    }

    @Operation(summary = "장비 부분 수정 (OWNER, 데모 차단)")
    @PatchMapping("/{deviceId}")
    public ResponseEntity<DeviceResponse> updateDevice(@AuthenticationPrincipal Long userId,
                                                        @PathVariable Long farmId,
                                                        @PathVariable Long deviceId,
                                                        @Valid @RequestBody DeviceRequest request) {
        return ResponseEntity.ok(deviceService.updateDevice(farmId, userId, deviceId, request));
    }

    @Operation(summary = "장비 삭제 — soft delete (OWNER, 데모 차단)")
    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> deleteDevice(@AuthenticationPrincipal Long userId,
                                              @PathVariable Long farmId,
                                              @PathVariable Long deviceId) {
        deviceService.deleteDevice(farmId, userId, deviceId);
        return ResponseEntity.noContent().build();
    }
}
