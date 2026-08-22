package com.smartfarm.service.controller;

import com.smartfarm.service.dto.EnvThresholdsRequest;
import com.smartfarm.service.dto.EnvThresholdsResponse;
import com.smartfarm.service.service.EnvThresholdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Environment", description = "환경 대시보드 API")
@RestController
@RequestMapping("/api/farms/{farmId}/env-thresholds")
@RequiredArgsConstructor
public class EnvThresholdController {

    private final EnvThresholdService envThresholdService;

    @Operation(summary = "환경 임계치 설정 조회 (멤버) — 미설정 시 enabled=false 기본값")
    @GetMapping
    public ResponseEntity<EnvThresholdsResponse> findThresholds(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long farmId) {
        return ResponseEntity.ok(envThresholdService.findThresholds(farmId, userId));
    }

    @Operation(summary = "환경 임계치 설정 전체 교체 (OWNER)")
    @PutMapping
    public ResponseEntity<EnvThresholdsResponse> updateThresholds(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long farmId,
            @Valid @RequestBody EnvThresholdsRequest request) {
        return ResponseEntity.ok(envThresholdService.updateThresholds(farmId, userId, request));
    }
}
