package com.smartfarm.service.scheduler;

import static org.mockito.ArgumentMatchers.any;
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
 * {@link EnvironmentSnapshotPoller} 단위 테스트(contract §4.6) — 정상 tick의 캐시 갱신·적재·평가
 * 트리거 순서, 중복(skip) tick의 평가 미실행, ai-server 장애 시 예외를 삼키고 로그만 남기는지를
 * 검증한다. 실제 HTTP·DB는 협력자를 Mockito로 대체(EnvironmentServiceUnitTest 선례와 동일 원칙).
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
    @DisplayName("정상 tick — 캐시 갱신, 적재, (새로 적재됐으므로) 임계치 평가까지 수행한다")
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
    @DisplayName("중복(skip) tick — 캐시는 갱신되지만 임계치 평가는 실행하지 않는다")
    void duplicateTickSkipsEvaluation() {
        when(aiEnvironmentClient.fetchToday()).thenReturn(RESPONSE);
        when(envSnapshotIngestService.ingest(RESPONSE)).thenReturn(Optional.empty());

        poller.poll();

        verify(environmentCache).put(any());
        verify(envThresholdAlertService, never()).evaluate(any());
    }

    @Test
    @DisplayName("ai-server 장애(D003)는 예외를 전파하지 않고 캐시/적재/평가 모두 건너뛴다")
    void aiServerFailureIsSwallowed() {
        when(aiEnvironmentClient.fetchToday()).thenThrow(new CustomException(ErrorCode.D003));

        poller.poll(); // 예외가 여기서 전파되지 않으면 성공

        verify(environmentCache, never()).put(any());
        verify(envSnapshotIngestService, never()).ingest(any());
        verify(envThresholdAlertService, never()).evaluate(any());
    }

    @Test
    @DisplayName("적재 중 예상 밖 예외도 전파하지 않는다")
    void ingestExceptionIsSwallowed() {
        when(aiEnvironmentClient.fetchToday()).thenReturn(RESPONSE);
        when(envSnapshotIngestService.ingest(RESPONSE)).thenThrow(new RuntimeException("db down"));

        poller.poll(); // 예외가 여기서 전파되지 않으면 성공

        verify(envThresholdAlertService, never()).evaluate(any());
    }
}
