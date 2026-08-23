package com.smartfarm.service.controller;

import com.smartfarm.service.dto.ForecastResponse;
import com.smartfarm.service.service.ForecastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 날씨예보(contract §4.8, 이슈 #56) — EnvironmentController와 최종 경로가 겹치지 않는 별도 컨트롤러로
 * 분리한다(핸드오프: 챗봇 #54와 병행 작업 중이라 기존 파일 수정 범위를 최소화).
 */
@Tag(name = "Forecast", description = "날씨예보 API")
@RestController
@RequestMapping("/api/farms/{farmId}/environment/forecast")
@RequiredArgsConstructor
public class ForecastController {

    private final ForecastService forecastService;

    @Operation(summary = "단기예보 조회 (멤버) — KMA 프록시, 전 농장 공용 60분 캐시+stale 폴백")
    @GetMapping
    public ResponseEntity<ForecastResponse> findForecast(@AuthenticationPrincipal Long userId,
                                                           @PathVariable Long farmId) {
        return ResponseEntity.ok(forecastService.findForecast(farmId, userId));
    }
}
