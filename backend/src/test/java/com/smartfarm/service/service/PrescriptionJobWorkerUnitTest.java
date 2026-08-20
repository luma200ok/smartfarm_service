package com.smartfarm.service.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartfarm.service.config.PrescriptionProperties;
import com.smartfarm.service.dto.AiPrescriptionResponse;
import com.smartfarm.service.dto.PrescriptionResult;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.service.PrescriptionTransitionService.PrescriptionJob;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 워커 실패 경로 단위 테스트 — Spring 없이 실제 단일 스레드 executor + Mockito 협력자로
 * 실패 시 상태 전이 호출이 정확한지(어느 전이가 호출되고 어느 전이가 호출되면 안 되는지) 검증한다.
 */
class PrescriptionJobWorkerUnitTest {

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(10), runnable -> {
        Thread thread = new Thread(runnable, "prescription-worker-test");
        thread.setDaemon(true);
        return thread;
    });

    private final PrescriptionTransitionService transitionService = mock(PrescriptionTransitionService.class);
    private final AiPrescriptionClient aiPrescriptionClient = mock(AiPrescriptionClient.class);
    private final PrescriptionWebhookNotifier webhookNotifier = mock(PrescriptionWebhookNotifier.class);
    private final PrescriptionProperties properties =
            new PrescriptionProperties(Duration.ofSeconds(1), List.of(Duration.ofMillis(10), Duration.ofMillis(10)));

    private final PrescriptionJobWorker worker =
            new PrescriptionJobWorker(executor, transitionService, aiPrescriptionClient, properties, webhookNotifier);

    private static final AiPrescriptionResponse OK_RESPONSE =
            new AiPrescriptionResponse("잎곰팡이병으로 보입니다", "습한 환경", "환기 강화", "아침 물주기", "3일 뒤", List.of());

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("픽업(markProcessing) 자체가 실패하면 아무 전이도 없이 종료된다 — 행은 PENDING 유지")
    void pickupFailureLeavesPendingUntouched() {
        when(transitionService.markProcessing(1L)).thenThrow(new RuntimeException("DB 접속 불가"));

        worker.submit(1L);

        verify(transitionService, timeout(1_000)).markProcessing(1L);
        verify(aiPrescriptionClient, after(300).never()).prescribe(any(), any());
        verify(transitionService, never()).markFailed(anyLong(), any());
        verify(transitionService, never()).markCompleted(anyLong(), any());
    }

    @Test
    @DisplayName("픽업이 empty(중복 제출/이미 처리)면 ai-server 호출 없이 스킵한다")
    void emptyPickupSkipsSilently() {
        when(transitionService.markProcessing(1L)).thenReturn(Optional.empty());

        worker.submit(1L);

        verify(transitionService, timeout(1_000)).markProcessing(1L);
        verify(aiPrescriptionClient, after(300).never()).prescribe(any(), any());
        verify(transitionService, never()).markFailed(anyLong(), any());
    }

    @Test
    @DisplayName("markCompleted가 실패하면 best-effort로 markFailed(P002)가 호출된다")
    void markCompletedFailureFallsBackToMarkFailed() {
        when(transitionService.markProcessing(1L))
                .thenReturn(Optional.of(new PrescriptionJob(1L, 100L, "질문", null)));
        when(aiPrescriptionClient.prescribe("질문", null)).thenReturn(OK_RESPONSE);
        doThrow(new RuntimeException("커밋 실패")).when(transitionService)
                .markCompleted(anyLong(), any(PrescriptionResult.class));

        worker.submit(1L);

        verify(transitionService, timeout(1_000)).markFailed(1L, ErrorCode.P002);
    }

    @Test
    @DisplayName("429(P003)가 재시도까지 전부 소진되면 markFailed(P003) — 시도는 정확히 3회")
    void retryExhaustionFailsWithP003() {
        when(transitionService.markProcessing(1L))
                .thenReturn(Optional.of(new PrescriptionJob(1L, 100L, "질문", null)));
        when(aiPrescriptionClient.prescribe("질문", null)).thenThrow(new CustomException(ErrorCode.P003));

        worker.submit(1L);

        verify(transitionService, timeout(1_000)).markFailed(1L, ErrorCode.P003);
        verify(aiPrescriptionClient, timeout(1_000).times(3)).prescribe("질문", null);
        verify(transitionService, never()).markCompleted(anyLong(), any());
    }
}
