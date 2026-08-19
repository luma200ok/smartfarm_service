package com.smartfarm.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배포 파이프라인 헬스체크 전용 — 인증 없이 접근 가능해야 하므로(SecurityConfig permitAll)
 * 버전·내부 정보는 절대 포함하지 않는다(docs/_local/handoff/infra-7-deploy-next.md P1 픽스).
 */
@Tag(name = "Health", description = "헬스체크 API")
@RestController
public class HealthController {

    @Operation(summary = "헬스체크 (무인증)")
    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
