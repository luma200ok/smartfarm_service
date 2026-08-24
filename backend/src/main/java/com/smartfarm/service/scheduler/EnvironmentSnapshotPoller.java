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
 * ai-server {@code GET /api/environment/today}를 60s 주기로 폴링해 env_snapshots에 적재하고,
 * 이어서 알람 규칙을 평가하는 스케줄러(contract §4.6·§4.13, 이슈 #52 → #118) — ai-server 무변경
 * 원칙. 기존 on-demand 요청 경로(EnvironmentService.findTodayEnvironment)는 폴백으로 그대로
 * 유지되고, 이 폴러는 그 60s 캐시를 선제적으로 갱신해 대시보드 진입 시 캐시 히트율을 높인다.
 *
 * <p>실패(ai-server 장애 포함)는 로그만 남기고 삼킨다(handoff) — 기존 findTodayEnvironment의
 * stale 캐시 폴백 로직에 영향을 주지 않는다(폴러가 실패해도 캐시는 이전 값을 유지할 뿐).
 *
 * <p>⚠️ <b>적재(ai-server)와 평가는 분리된 두 단계다</b>(#118 리뷰 P1-1). #117까지는 ai-server의
 * indoor가 유일한 데이터 소스라 "적재된 tick에서만 평가"가 정당했지만, #118부터
 * {@code SENSOR_READING}(sensor_readings)·{@code DEVICE_HEARTBEAT}(devices)은 자체 데이터 소스를
 * 갖는다. 평가를 적재 성공에 매달아 두면 ai-server 다운·타임아웃·중복 tick에서 그 두 소스가 통째로
 * 침묵하고, 자동 해소까지 멈춰 정상 복귀한 알람이 닫히지 않는다. 특히 통신 두절 규칙은
 * <b>인프라 장애를 잡으라고 있는 것</b>이라 ai-server가 함께 죽는 장애에서 침묵하면 존재 이유가
 * 사라진다.
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
        AiEnvironmentResponse.Indoor freshIndoor = null;
        try {
            AiEnvironmentResponse response = aiEnvironmentClient.fetchToday();
            environmentCache.put(EnvironmentTodayResponse.from(response));
            // 이번 tick에 실제로 새로 적재됐을 때만 indoor를 평가에 넘긴다 — 중복(skip) tick의
            // indoor는 직전 tick과 같은 stale 값이라, 그걸로 ENV_SNAPSHOT 규칙을 판정하면 ai-server
            // 상태 파일이 멈춘 동안 옛 값으로 알람을 발동시키거나 해소해 버린다.
            if (envSnapshotIngestService.ingest(response).isPresent()) {
                freshIndoor = response.indoor();
            }
        } catch (Exception e) {
            log.warn("환경 스냅샷 폴링 실패(로그만, 캐시/적재 미갱신): {}", e.getClass().getSimpleName());
        }

        // 적재 실패·중복 tick이어도 평가는 돈다(위 클래스 주석 P1-1). indoor가 null이면
        // ENV_SNAPSHOT 규칙만 "관측 부재"로 건너뛰며, 그때 그 규칙의 누적 이탈 시간(firstBreachAt)은
        // 보존된다 — 관측이 끊긴 것은 정상 복귀가 아니므로 이탈 구간을 리셋하지 않는다.
        // 평가 실패도 여기서 삼킨다 — 스케줄러 스레드로 예외가 전파되면 @Scheduled fixedDelay
        // 체인이 끊길 수 있고, 개별 규칙 격리는 evaluate() 내부가 이미 담당한다(회귀-A).
        try {
            envThresholdAlertService.evaluate(freshIndoor);
        } catch (Exception e) {
            log.warn("알람 규칙 평가 실패(로그만): {}", e.getClass().getSimpleName());
        }
    }
}
