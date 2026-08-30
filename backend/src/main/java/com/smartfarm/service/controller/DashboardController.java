package com.smartfarm.service.controller;

import com.smartfarm.service.dto.DashboardFarmsResponse;
import com.smartfarm.service.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "홈 대시보드 집계 API")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "홈 대시보드 농장 카드 집계 (인증) — 내 활성 농장 전체를 배치 조회로 "
            + "한 번에 반환한다(N+1 방지, 이슈 #139). PENDING(승인 대기) 멤버십 농장은 제외된다 — "
            + "이 응답이 싣는 랙/층 수·지표·알람은 farm-scoped 조회 표면과 동일한 내부 데이터라 "
            + "PENDING이 접근할 수 없다. 집계 상한(dashboard.max-farms) 초과 시 조용히 자르지 않고 "
            + "totalCount·truncated로 절단 여부를 명시한다(이슈 #140).")
    @GetMapping("/farms")
    public ResponseEntity<DashboardFarmsResponse> findMyFarmsDashboard(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(dashboardService.findMyFarmsDashboard(userId));
    }
}
