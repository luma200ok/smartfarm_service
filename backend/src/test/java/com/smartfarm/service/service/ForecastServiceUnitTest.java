package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartfarm.service.dto.ForecastResponse;
import com.smartfarm.service.dto.SkyCondition;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Spring 없이 실제 협력자(ForecastCache) + Mockito(FarmAccessGuard·KmaForecastClient)로 캐시
 * 히트/만료 폴백 경로를 검증한다(EnvironmentServiceUnitTest와 동일 이유 — 60분 실TTL을 실시간으로
 * 기다릴 수 없어 짧은 TTL(package-private 생성자)로 그 경로를 검증한다).
 */
class ForecastServiceUnitTest {

    private static final ForecastResponse FORECAST = new ForecastResponse(
            LocalDateTime.of(2026, 8, 23, 8, 0),
            List.of(new ForecastResponse.Point(LocalDateTime.of(2026, 8, 23, 9, 0), 23.0, 55.0,
                    SkyCondition.SUNNY, 20))
    );

    private final FarmAccessGuard farmAccessGuard = mock(FarmAccessGuard.class);
    private final KmaForecastClient kmaForecastClient = mock(KmaForecastClient.class);

    @Test
    @DisplayName("가드를 먼저 통과시킨 뒤에만 캐시/KMA 조회를 시도한다")
    void guardIsCheckedFirst() {
        ForecastCache cache = new ForecastCache(Duration.ofMinutes(60));
        ForecastService service = new ForecastService(farmAccessGuard, kmaForecastClient, cache);
        when(farmAccessGuard.requireMember(anyLong(), anyLong()))
                .thenThrow(new CustomException(ErrorCode.F002));

        assertThatThrownBy(() -> service.findForecast(1L, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.F002));

        verify(kmaForecastClient, never()).fetchForecast();
    }

    @Test
    @DisplayName("신선한 캐시가 있으면 KMA를 다시 호출하지 않는다")
    void freshCacheSkipsKmaCall() {
        ForecastCache cache = new ForecastCache(Duration.ofMinutes(60));
        cache.put(FORECAST);
        ForecastService service = new ForecastService(farmAccessGuard, kmaForecastClient, cache);

        ForecastResponse result = service.findForecast(1L, 1L);

        assertThat(result.points()).hasSize(1);
        verify(kmaForecastClient, never()).fetchForecast();
    }

    @Test
    @DisplayName("캐시 만료 후 KMA가 장애(W001)여도 만료된 캐시 값을 폴백으로 반환한다")
    void staleCacheFallsBackOnKmaFailure() {
        ForecastCache cache = new ForecastCache(Duration.ofMillis(10));
        cache.put(FORECAST);
        awaitCacheExpiry(cache);

        when(kmaForecastClient.fetchForecast()).thenThrow(new CustomException(ErrorCode.W001));
        ForecastService service = new ForecastService(farmAccessGuard, kmaForecastClient, cache);

        ForecastResponse result = service.findForecast(1L, 1L);

        assertThat(result).isEqualTo(FORECAST);
    }

    @Test
    @DisplayName("캐시가 아예 없는 상태에서 KMA 장애면 W001을 그대로 던진다")
    void noCacheAndKmaFailureThrows() {
        ForecastCache cache = new ForecastCache(Duration.ofMinutes(60));
        when(kmaForecastClient.fetchForecast()).thenThrow(new CustomException(ErrorCode.W001));
        ForecastService service = new ForecastService(farmAccessGuard, kmaForecastClient, cache);

        assertThatThrownBy(() -> service.findForecast(1L, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.W001));
    }

    @Test
    @DisplayName("정상 응답은 캐시에 저장된다")
    void successResponseIsCached() {
        ForecastCache cache = new ForecastCache(Duration.ofMinutes(60));
        when(kmaForecastClient.fetchForecast()).thenReturn(FORECAST);
        ForecastService service = new ForecastService(farmAccessGuard, kmaForecastClient, cache);

        ForecastResponse result = service.findForecast(1L, 1L);

        assertThat(result).isEqualTo(FORECAST);
        assertThat(cache.getIfFresh()).contains(FORECAST);
    }

    /** 외부 대기 라이브러리 없이 TTL 만료를 폴링 확정한다(1s 상한 — 실패 시 무한 대기 방지). */
    private void awaitCacheExpiry(ForecastCache cache) {
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
