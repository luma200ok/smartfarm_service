package com.smartfarm.service.service;

import com.smartfarm.service.entity.SensorMetric;
import java.util.Map;
import java.util.Set;

/**
 * 1틱·1농장 분량의 제어 반영 입력(contract §4.12 시뮬레이터 연동) — 시뮬레이터가 값을 만들 때
 * "이 존의 이 지표에 목표값이 있는가, 그 존의 제어기가 꺼져 있지는 않은가, 직전 값은 얼마였는가"를
 * 결정적으로 답한다. 조회는 {@link ControlSimulationContextProvider}가 농장당 한 번에 끝낸다(N+1 방지).
 *
 * @param targetsByZone       존별 지표 목표값({@code ControlSetpoint})
 * @param uncontrolledZoneIds 꺼진 제어기({@code kind=CONTROLLER, status=OFF})가 있는 존 — 제어기가
 *                            꺼졌으니 목표로 수렴시키지 않고 자연 표류시킨다(contract §4.12)
 * @param previousValues      장비×지표 직전 측정값 — 수렴 시작점
 */
public record ControlSimulationContext(
        Map<Long, Map<SensorMetric, Double>> targetsByZone,
        Set<Long> uncontrolledZoneIds,
        Map<ControlSimulationContext.DeviceMetricKey, Double> previousValues
) {

    /** 직전 값 조회 키 — 문자열 연결 키는 오타·구분자 충돌에 약해 레코드로 둔다. */
    public record DeviceMetricKey(Long deviceId, SensorMetric metric) {
    }

    public static ControlSimulationContext empty() {
        return new ControlSimulationContext(Map.of(), Set.of(), Map.of());
    }

    /**
     * 이 존·지표에 적용할 목표값 — 없거나(미설정) 그 존의 제어기가 꺼져 있으면 {@code null}
     * (= 목표 수렴 없이 기존 상수 기저 유지, contract §4.12).
     */
    public Double targetFor(Long zoneId, SensorMetric metric) {
        if (zoneId == null || uncontrolledZoneIds.contains(zoneId)) {
            return null;
        }
        return targetsByZone.getOrDefault(zoneId, Map.of()).get(metric);
    }

    /**
     * 목표가 설정돼 있지만 그 존의 제어기가 꺼져 <b>수렴이 해제된</b> 상태인가(리뷰 P3). 이 경우
     * 값은 자연 생성값으로 <b>같은 비율로 되돌아간다</b> — 즉시 자연값으로 점프시키면 제어기를 끄는
     * 순간 그래프에 계단이 생긴다(계약이 목표 수렴에서 피하려던 것과 같은 문제가 반대 방향으로 난다).
     */
    public boolean isReleased(Long zoneId, SensorMetric metric) {
        return zoneId != null
                && uncontrolledZoneIds.contains(zoneId)
                && targetsByZone.getOrDefault(zoneId, Map.of()).containsKey(metric);
    }

    /** 장비×지표 직전 측정값 — 없으면 {@code null}(첫 tick이라 자연 생성값에서 출발한다). */
    public Double previousValueOf(Long deviceId, SensorMetric metric) {
        return previousValues.get(new DeviceMetricKey(deviceId, metric));
    }
}
