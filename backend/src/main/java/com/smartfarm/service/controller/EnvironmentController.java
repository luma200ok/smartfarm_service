package com.smartfarm.service.controller;

import com.smartfarm.service.dto.EnvironmentHistoryResponse;
import com.smartfarm.service.dto.EnvironmentTodayResponse;
import com.smartfarm.service.service.EnvironmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Environment", description = "환경 대시보드 API")
@RestController
@RequestMapping("/api/farms/{farmId}/environment")
@RequiredArgsConstructor
public class EnvironmentController {

    private final EnvironmentService environmentService;

    @Operation(summary = "금일 환경 데이터 조회 (멤버) — ai-server 프록시, 전 농장 공용 60s 캐시")
    @GetMapping("/today")
    public ResponseEntity<EnvironmentTodayResponse> findTodayEnvironment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long farmId) {
        return ResponseEntity.ok(environmentService.findTodayEnvironment(farmId, userId));
    }

    @Operation(summary = "환경 시계열 이력 조회 (멤버) — range=24h|7d|30d(기본 24h), 7d/30d는 DB 다운샘플 집계")
    @GetMapping("/history")
    public ResponseEntity<EnvironmentHistoryResponse> findHistory(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long farmId,
            @RequestParam(required = false) String range) {
        return ResponseEntity.ok(environmentService.findHistory(farmId, userId, range));
    }
}
