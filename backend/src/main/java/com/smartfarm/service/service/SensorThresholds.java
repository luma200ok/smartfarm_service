package com.smartfarm.service.service;

import com.smartfarm.service.entity.SensorMetric;
import java.util.EnumMap;
import java.util.Map;

/**
 * 랙 도면 셀 상태(contract §4.11 {@code ReadingMatrixResponse.state})·층별 비교(level-summary)
 * 목표 대비 편차 판정용 <b>1차 지표별 상수 기본범위</b>. contract: "판정 기준은 farm_env_thresholds가
 * 아직 온·습도만 다루므로 1차는 지표별 상수 기본범위를 쓰고, 임계치 확장은 사이클 3(알람)으로
 * 미룬다" — 사이클 3에서 이 클래스를 {@code farm_env_thresholds} 확장으로 대체할 예정.
 *
 * <p>OK 범위를 벗어나되 WARNING 여유폭 이내면 WARNING, 그 이상 벗어나면 CRITICAL. IDLE은 값 자체가
 * 없을 때(데이터 없음)만 서비스 계층이 별도로 판정한다(이 클래스는 값이 있는 경우만 다룬다).
 */
final class SensorThresholds {

    private record Range(double min, double max, double warningBuffer) {
        double target() {
            return (min + max) / 2.0;
        }
    }

    private static final Map<SensorMetric, Range> RANGES = new EnumMap<>(SensorMetric.class);

    static {
        RANGES.put(SensorMetric.TEMPERATURE, new Range(15.0, 30.0, 3.0));
        RANGES.put(SensorMetric.HUMIDITY, new Range(40.0, 85.0, 10.0));
        RANGES.put(SensorMetric.CO2, new Range(350.0, 1500.0, 300.0));
        RANGES.put(SensorMetric.EC, new Range(0.5, 4.0, 0.5));
        RANGES.put(SensorMetric.PH, new Range(5.0, 7.0, 0.5));
        RANGES.put(SensorMetric.PPFD, new Range(0.0, 900.0, 100.0));
        RANGES.put(SensorMetric.POWER, new Range(0.0, 20.0, 5.0));
    }

    private SensorThresholds() {
    }

    enum State {
        OK, WARNING, CRITICAL, IDLE
    }

    static State stateOf(SensorMetric metric, double value) {
        Range range = RANGES.get(metric);
        if (value >= range.min() && value <= range.max()) {
            return State.OK;
        }
        double distance = value < range.min() ? range.min() - value : value - range.max();
        return distance <= range.warningBuffer() ? State.WARNING : State.CRITICAL;
    }

    /** level-summary 편차(%) 계산 기준값 — OK 범위의 중앙값을 "목표"로 삼는다. */
    static double targetOf(SensorMetric metric) {
        return RANGES.get(metric).target();
    }

    static double deviationPercent(SensorMetric metric, double average) {
        double target = targetOf(metric);
        if (target == 0.0) {
            return 0.0;
        }
        return (average - target) / target * 100.0;
    }
}
