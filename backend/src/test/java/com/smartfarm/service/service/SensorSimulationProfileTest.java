package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.entity.SensorMetric;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 가상 장비 시뮬레이터 값 계산 단위 테스트(contract §4.11, 이슈 #90) — 결정적 노이즈 재현성,
 * 층별 오프셋 방향, 일주기 변동을 검증한다.
 */
class SensorSimulationProfileTest {

    private static final LocalDateTime MEASURED_AT = LocalDateTime.of(2026, 8, 23, 14, 30);

    @Test
    @DisplayName("같은 (deviceId, measuredAt 분) 입력이면 항상 같은 값을 낸다 — 재현성")
    void sameInputProducesSameValue() {
        double first = SensorSimulationProfile.simulate(SensorMetric.TEMPERATURE, 3, 42L, MEASURED_AT);
        double second = SensorSimulationProfile.simulate(SensorMetric.TEMPERATURE, 3, 42L, MEASURED_AT);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("같은 분 내(초만 다름) 재계산해도 같은 값을 낸다 — 시드가 분 단위이기 때문")
    void sameMinuteProducesSameValueRegardlessOfSeconds() {
        double atZeroSeconds = SensorSimulationProfile.simulate(
                SensorMetric.TEMPERATURE, 3, 42L, MEASURED_AT.withSecond(0));
        double atFortySeconds = SensorSimulationProfile.simulate(
                SensorMetric.TEMPERATURE, 3, 42L, MEASURED_AT.withSecond(40));

        assertThat(atZeroSeconds).isEqualTo(atFortySeconds);
    }

    @Test
    @DisplayName("deviceId가 다르면 노이즈가 달라져 값도 달라진다")
    void differentDeviceIdProducesDifferentValue() {
        double device1 = SensorSimulationProfile.simulate(SensorMetric.TEMPERATURE, 3, 1L, MEASURED_AT);
        double device2 = SensorSimulationProfile.simulate(SensorMetric.TEMPERATURE, 3, 2L, MEASURED_AT);

        assertThat(device1).isNotEqualTo(device2);
    }

    @Test
    @DisplayName("층별 오프셋 — 온도는 층이 높을수록(levelOffsetStep 양수) 값이 커진다(노이즈를 상쇄할 만큼 큰 차이로 확인)")
    void temperatureIncreasesWithHigherLevel() {
        double level1 = SensorSimulationProfile.simulate(SensorMetric.TEMPERATURE, 1, 1L, MEASURED_AT);
        double level10 = SensorSimulationProfile.simulate(SensorMetric.TEMPERATURE, 10, 1L, MEASURED_AT);

        // levelOffsetStep(0.5) * 9층 차이 = 4.5, 노이즈 진폭(±0.3)보다 훨씬 커서 부호가 뒤집히지 않는다.
        assertThat(level10 - level1).isGreaterThan(3.0);
    }

    @Test
    @DisplayName("습도는 0 미만으로 내려가지 않는다(floor)")
    void humidityNeverGoesNegative() {
        for (int hour = 0; hour < 24; hour++) {
            double value = SensorSimulationProfile.simulate(
                    SensorMetric.HUMIDITY, 50, 999L, MEASURED_AT.withHour(hour));
            assertThat(value).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Test
    @DisplayName("습도는 100%를 넘지 않는다(ceiling — 사이클 3 리뷰 P3)")
    void humidityNeverExceedsHundred() {
        // 기저 65 + 진폭 8 + 층 오프셋(-1/층)이라 자연 생성만으로는 100을 넘지 않지만, 목표 수렴은
        // 사용자가 지정한 목표(최대 100)에 노이즈(±2)가 얹혀 상한을 넘을 수 있다 — 두 경로 모두 확인.
        for (int hour = 0; hour < 24; hour++) {
            assertThat(SensorSimulationProfile.simulate(
                    SensorMetric.HUMIDITY, 1, 999L, MEASURED_AT.withHour(hour))).isLessThanOrEqualTo(100.0);
        }
        for (long deviceId = 1; deviceId <= 50; deviceId++) {
            assertThat(SensorSimulationProfile.converge(
                    SensorMetric.HUMIDITY, 100.0, 100.0, deviceId, MEASURED_AT)).isLessThanOrEqualTo(100.0);
        }
    }

    @Test
    @DisplayName("목표 수렴은 직전 값과 목표 사이 한 걸음이다 — 즉시 점프하지 않는다")
    void convergeMovesOneStepTowardTarget() {
        double moved = SensorSimulationProfile.converge(SensorMetric.TEMPERATURE, 10.0, 30.0, 1L, MEASURED_AT);

        // 비율 0.2 → 10 + 20*0.2 = 14, 노이즈 진폭 ±0.3.
        assertThat(moved).isBetween(13.7, 14.3);
    }

    @Test
    @DisplayName("7종 지표 전부 계산 가능하고 NaN/Infinite이 아니다")
    void allMetricsProduceFiniteValues() {
        for (SensorMetric metric : SensorMetric.values()) {
            double value = SensorSimulationProfile.simulate(metric, 5, 7L, MEASURED_AT);
            assertThat(value).isFinite();
        }
    }
}
