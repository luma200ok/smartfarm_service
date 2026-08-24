package com.smartfarm.service.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartfarm.service.dto.AiEnvironmentResponse;
import com.smartfarm.service.entity.EnvSnapshot;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.service.AiEnvironmentClient;
import com.smartfarm.service.service.EnvSnapshotIngestService;
import com.smartfarm.service.service.EnvThresholdAlertService;
import com.smartfarm.service.service.EnvironmentCache;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link EnvironmentSnapshotPoller} 단위 테스트(contract §4.6·§4.13) — 정상 tick의 캐시 갱신·적재·
 * 평가 트리거, <b>적재 실패·중복 tick에서도 평가가 계속 도는지</b>(#118 리뷰 P1-1), 그리고 그때
 * indoor를 null(관측 부재)로 넘겨 stale 값 오발동을 막는지를 검증한다. 실제 HTTP·DB는 협력자를
 * Mockito로 대체(EnvironmentServiceUnitTest 선례와 동일 원칙).
 */
class EnvironmentSnapshotPollerUnitTest {

    private static final AiEnvironmentResponse RESPONSE = new AiEnvironmentResponse(
            true,
            LocalDateTime.of(2026, 8, 22, 9, 0),
            new AiEnvironmentResponse.Weather(28.5, 62.0),
            new AiEnvironmentResponse.Indoor(24.1, 55.3, true),
            List.of(),
            List.of());

    private final AiEnvironmentClient aiEnvironmentClient = mock(AiEnvironmentClient.class);
    private final EnvironmentCache environmentCache = mock(EnvironmentCache.class);
    private final EnvSnapshotIngestService envSnapshotIngestService = mock(EnvSnapshotIngestService.class);
    private final EnvThresholdAlertService envThresholdAlertService = mock(EnvThresholdAlertService.class);

    private final EnvironmentSnapshotPoller poller = new EnvironmentSnapshotPoller(
            aiEnvironmentClient, environmentCache, envSnapshotIngestService, envThresholdAlertService);

    @Test
    @DisplayName("정상 tick — 캐시 갱신, 적재, (새로 적재됐으므로) indoor를 실어 알람 규칙을 평가한다")
    void newTickUpdatesCacheIngestsAndEvaluates() {
        when(aiEnvironmentClient.fetchToday()).thenReturn(RESPONSE);
        EnvSnapshot saved = mock(EnvSnapshot.class);
        when(envSnapshotIngestService.ingest(RESPONSE)).thenReturn(Optional.of(saved));

        poller.poll();

        verify(environmentCache).put(any());
        verify(envSnapshotIngestService).ingest(RESPONSE);
        verify(envThresholdAlertService).evaluate(RESPONSE.indoor());
    }

    @Test
    @DisplayName("P1-1: 중복(skip) tick에서도 평가는 돈다 — 단 indoor는 null(관측 부재)로 넘겨 "
            + "직전 tick과 같은 stale 값으로 ENV_SNAPSHOT 규칙이 오발동하지 않게 한다"
            + "(#117까지는 여기서 평가를 통째로 건너뛰어 센서·통신두절 규칙까지 침묵했다)")
    void duplicateTickStillEvaluatesWithoutIndoor() {
        when(aiEnvironmentClient.fetchToday()).thenReturn(RESPONSE);
        when(envSnapshotIngestService.ingest(RESPONSE)).thenReturn(Optional.empty());

        poller.poll();

        verify(environmentCache).put(any());
        verify(envThresholdAlertService).evaluate(isNull());
    }

    @Test
    @DisplayName("P1-1: ai-server 장애(D003)에도 평가는 돈다 — SENSOR_READING·DEVICE_HEARTBEAT은 "
            + "자체 데이터 소스가 있고, 특히 통신 두절 규칙은 ai-server가 함께 죽는 장애를 잡으라고 "
            + "있는 것이다. 예외는 여전히 전파하지 않는다")
    void aiServerFailureStillEvaluatesOtherSources() {
        when(aiEnvironmentClient.fetchToday()).thenThrow(new CustomException(ErrorCode.D003));

        poller.poll(); // 예외가 여기서 전파되지 않으면 성공

        verify(environmentCache, never()).put(any());
        verify(envSnapshotIngestService, never()).ingest(any());
        verify(envThresholdAlertService).evaluate(isNull());
    }

    @Test
    @DisplayName("적재 중 예상 밖 예외도 전파하지 않으며, 그 tick에도 평가는 돈다")
    void ingestExceptionIsSwallowedButEvaluationStillRuns() {
        when(aiEnvironmentClient.fetchToday()).thenReturn(RESPONSE);
        when(envSnapshotIngestService.ingest(RESPONSE)).thenThrow(new RuntimeException("db down"));

        poller.poll(); // 예외가 여기서 전파되지 않으면 성공

        verify(envThresholdAlertService).evaluate(isNull());
    }

    @Test
    @DisplayName("평가 자체가 던지는 예외도 스케줄러 스레드로 전파하지 않는다"
            + "(@Scheduled fixedDelay 체인이 끊기면 이후 모든 tick이 멈춘다)")
    void evaluationExceptionIsSwallowed() {
        when(aiEnvironmentClient.fetchToday()).thenReturn(RESPONSE);
        when(envSnapshotIngestService.ingest(RESPONSE)).thenReturn(Optional.of(mock(EnvSnapshot.class)));
        org.mockito.Mockito.doThrow(new RuntimeException("evaluate 폭발"))
                .when(envThresholdAlertService).evaluate(any());

        poller.poll(); // 예외가 여기서 전파되지 않으면 성공

        verify(envThresholdAlertService).evaluate(RESPONSE.indoor());
    }
}
