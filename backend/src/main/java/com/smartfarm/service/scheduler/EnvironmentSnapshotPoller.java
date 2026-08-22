package com.smartfarm.service.scheduler;

import com.smartfarm.service.dto.AiEnvironmentResponse;
import com.smartfarm.service.dto.EnvironmentTodayResponse;
import com.smartfarm.service.service.AiEnvironmentClient;
import com.smartfarm.service.service.EnvSnapshotIngestService;
import com.smartfarm.service.service.EnvThresholdAlertService;
import com.smartfarm.service.service.EnvironmentCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ai-server {@code GET /api/environment/today}를 60s 주기로 폴링해 env_snapshots에 적재하는
 * 스케줄러(contract §4.6, 이슈 #52) — ai-server 무변경 원칙. 기존 on-demand 요청 경로
 * (EnvironmentService.findTodayEnvironment)는 폴백으로 그대로 유지되고, 이 폴러는 그 60s 캐시를
 * 선제적으로 갱신해 대시보드 진입 시 캐시 히트율을 높인다.
 *
 * <p>실패(ai-server 장애 포함)는 로그만 남기고 삼킨다(handoff) — 기존 findTodayEnvironment의
 * stale 캐시 폴백 로직에 영향을 주지 않는다(폴러가 실패해도 캐시는 이전 값을 유지할 뿐).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnvironmentSnapshotPoller {

    private final AiEnvironmentClient aiEnvironmentClient;
    private final EnvironmentCache environmentCache;
    private final EnvSnapshotIngestService envSnapshotIngestService;
    private final EnvThresholdAlertService envThresholdAlertService;

    @Scheduled(initialDelayString = "${environment-snapshot.poll-initial-delay:PT60S}",
            fixedDelayString = "${environment-snapshot.poll-fixed-delay:PT60S}")
    public void poll() {
        try {
            AiEnvironmentResponse response = aiEnvironmentClient.fetchToday();
            environmentCache.put(EnvironmentTodayResponse.from(response));
            // 중복(skip)이 아니라 실제로 새로 적재된 tick에서만 임계치를 평가한다(contract:
            // "적재 직후" 평가 — 동일 데이터 재평가로 연속 이탈 카운트가 부풀려지는 것을 방지).
            envSnapshotIngestService.ingest(response)
                    .ifPresent(snapshot -> envThresholdAlertService.evaluate(response.indoor()));
        } catch (Exception e) {
            log.warn("환경 스냅샷 폴링 실패(로그만, 캐시/적재 미갱신): {}", e.getClass().getSimpleName());
        }
    }
}
