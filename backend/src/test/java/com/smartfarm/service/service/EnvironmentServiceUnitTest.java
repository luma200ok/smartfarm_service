package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartfarm.service.dto.AiEnvironmentResponse;
import com.smartfarm.service.dto.EnvironmentTodayResponse;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Spring 없이 실제 협력자(EnvironmentCache) + Mockito(FarmAccessGuard·AiEnvironmentClient)로
 * 캐시 히트/만료 폴백 경로를 검증한다(EnvironmentApiIntegrationTest는 60s 실TTL을 실시간으로
 * 기다릴 수 없어 "장애 시에도 만료된 캐시를 폴백으로 쓴다" 케이스를 커버하지 못한다 — 여기서
 * 짧은 TTL(package-private 생성자)로 그 경로를 검증한다).
 */
class EnvironmentServiceUnitTest {

    private static final AiEnvironmentResponse AI_RESPONSE = new AiEnvironmentResponse(
            true,
            LocalDateTime.of(2026, 8, 20, 9, 0),
            new AiEnvironmentResponse.Weather(28.5, 62.0),
            new AiEnvironmentResponse.Indoor(24.1, 55.3, true),
            List.of(new AiEnvironmentResponse.Device("cooling_fan", true)),
            List.of()
    );

    private final FarmAccessGuard farmAccessGuard = mock(FarmAccessGuard.class);
    private final AiEnvironmentClient aiEnvironmentClient = mock(AiEnvironmentClient.class);

    @Test
    @DisplayName("가드를 먼저 통과시킨 뒤에만 캐시/ai-server 조회를 시도한다")
    void guardIsCheckedFirst() {
        EnvironmentCache cache = new EnvironmentCache(Duration.ofSeconds(60));
        EnvironmentService service = new EnvironmentService(farmAccessGuard, aiEnvironmentClient, cache);
        when(farmAccessGuard.requireMember(anyLong(), anyLong()))
                .thenThrow(new CustomException(ErrorCode.F002));

        assertThatThrownBy(() -> service.findTodayEnvironment(1L, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.F002));

        verify(aiEnvironmentClient, never()).fetchToday();
    }

    @Test
    @DisplayName("신선한 캐시가 있으면 ai-server를 다시 호출하지 않는다")
    void freshCacheSkipsAiServerCall() {
        EnvironmentCache cache = new EnvironmentCache(Duration.ofSeconds(60));
        cache.put(EnvironmentTodayResponse.from(AI_RESPONSE));
        EnvironmentService service = new EnvironmentService(farmAccessGuard, aiEnvironmentClient, cache);

        EnvironmentTodayResponse result = service.findTodayEnvironment(1L, 1L);

        assertThat(result.outdoor().temp()).isEqualTo(28.5);
        verify(aiEnvironmentClient, never()).fetchToday();
    }

    @Test
    @DisplayName("캐시 만료 후 ai-server가 장애(D003)여도 만료된 캐시 값을 폴백으로 반환한다")
    void staleCacheFallsBackOnAiServerFailure() {
        EnvironmentCache cache = new EnvironmentCache(Duration.ofMillis(10));
        cache.put(EnvironmentTodayResponse.from(AI_RESPONSE));
        awaitCacheExpiry(cache);

        when(aiEnvironmentClient.fetchToday()).thenThrow(new CustomException(ErrorCode.D003));
        EnvironmentService service = new EnvironmentService(farmAccessGuard, aiEnvironmentClient, cache);

        EnvironmentTodayResponse result = service.findTodayEnvironment(1L, 1L);

        assertThat(result.outdoor().temp()).isEqualTo(28.5);
    }

    @Test
    @DisplayName("캐시가 아예 없는 상태에서 ai-server 장애면 D003을 그대로 던진다")
    void noCacheAndAiServerFailureThrows() {
        EnvironmentCache cache = new EnvironmentCache(Duration.ofSeconds(60));
        when(aiEnvironmentClient.fetchToday()).thenThrow(new CustomException(ErrorCode.D003));
        EnvironmentService service = new EnvironmentService(farmAccessGuard, aiEnvironmentClient, cache);

        assertThatThrownBy(() -> service.findTodayEnvironment(1L, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.D003));
    }

    @Test
    @DisplayName("정상 응답은 camelCase로 변환되고 캐시에 저장된다")
    void successResponseIsMappedAndCached() {
        EnvironmentCache cache = new EnvironmentCache(Duration.ofSeconds(60));
        when(aiEnvironmentClient.fetchToday()).thenReturn(AI_RESPONSE);
        EnvironmentService service = new EnvironmentService(farmAccessGuard, aiEnvironmentClient, cache);

        EnvironmentTodayResponse result = service.findTodayEnvironment(1L, 1L);

        assertThat(result.demo()).isTrue();
        assertThat(result.indoor().controlled()).isTrue();
        assertThat(result.devices()).hasSize(1);
        assertThat(cache.getIfFresh()).contains(result);
    }

    /** 외부 대기 라이브러리 없이 TTL 만료를 폴링 확정한다(1s 상한 — 실패 시 무한 대기 방지). */
    private void awaitCacheExpiry(EnvironmentCache cache) {
        long deadline = System.currentTimeMillis() + 1000;
        while (cache.getIfFresh().isPresent()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("캐시가 1s 내에 만료되지 않았다");
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("대기 중 인터럽트", e);
            }
        }
    }
}
