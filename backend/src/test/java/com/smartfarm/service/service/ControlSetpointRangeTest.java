package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.entity.SensorMetric;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 목표값 허용 범위 표와 제어 가능 지표 목록의 <b>일치 고정</b>(사이클 3 리뷰 P3) — 한쪽은
 * {@code SensorMetric#isControllable}에서 동적으로 도출하고 다른 한쪽은 손으로 적은 표라,
 * 5번째 제어 지표가 추가되면 {@code TARGET_RANGES.get(metric)}가 null이 되어 목표값 적재가
 * NPE(500)로 떨어진다. 그 어긋남을 컴파일이 아니라 이 테스트가 잡는다.
 */
class ControlSetpointRangeTest {

    @Test
    @DisplayName("제어 가능 지표 전부에 목표값 허용 범위가 정의돼 있고, 그 외 지표는 정의되지 않는다")
    void targetRangesCoverExactlyControllableMetrics() {
        assertThat(ControlService.TARGET_RANGES.keySet())
                .as("제어 지표를 추가·삭제하면 TARGET_RANGES도 함께 갱신해야 한다")
                .containsExactlyInAnyOrderElementsOf(ControlService.CONTROLLABLE_METRICS);
    }

    @Test
    @DisplayName("제어 가능 지표는 계약이 정한 4종(TEMPERATURE·HUMIDITY·CO2·PPFD)이다")
    void controllableMetricsMatchContract() {
        assertThat(ControlService.CONTROLLABLE_METRICS).containsExactly(
                SensorMetric.TEMPERATURE, SensorMetric.HUMIDITY, SensorMetric.CO2, SensorMetric.PPFD);
    }

    @Test
    @DisplayName("모든 허용 범위는 min < max이고 유한하다")
    void rangesAreWellFormed() {
        assertThat(ControlService.TARGET_RANGES.values()).allSatisfy(range -> {
            assertThat(range.min()).isFinite();
            assertThat(range.max()).isFinite();
            assertThat(range.min()).isLessThan(range.max());
        });
    }
}
