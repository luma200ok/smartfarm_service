package com.smartfarm.service.controller;

import com.smartfarm.service.dto.FarmBriefingResponse;
import com.smartfarm.service.service.BriefingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 홈 화면 "오늘 할일" 브리핑 API(이슈 #129-B) — 기존 조회를 재사용한 집계 단일 엔드포인트. */
@Tag(name = "Briefing", description = "홈 화면 브리핑 API")
@RestController
@RequestMapping("/api/farms/{farmId}/briefing")
@RequiredArgsConstructor
public class BriefingController {

    private final BriefingService briefingService;

    @Operation(summary = "오늘 할일 브리핑 (멤버) — 미확인 알람 건수 + 보정 기한 임박 장비 수"
            + "(harvestDueSoon 없음 — 후속 #130)")
    @GetMapping
    public ResponseEntity<FarmBriefingResponse> briefing(@AuthenticationPrincipal Long userId,
                                                           @PathVariable Long farmId) {
        return ResponseEntity.ok(briefingService.briefing(farmId, userId));
    }
}
