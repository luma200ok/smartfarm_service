package com.smartfarm.service.service;

import com.smartfarm.service.entity.SensorMetric;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * 가상 장비 시뮬레이터 값 생성(contract §4.11, 이슈 #90) — "실기기·실센서가 없으므로 측정값은
 * 백엔드가 생성한다"는 handoff 결정에 따른 순수 함수 계산기. 값 = <b>일주기 기저(sin) + 층별
 * 오프셋 + 결정적 노이즈</b> 3항으로 구성한다.
 *
 * <p>기저·진폭·층별 오프셋 계수는 실측·문헌 인용이 필요한 §4.9 양액 프리셋과 달리 "그럴듯한 데모
 * 데이터"가 목적이므로(응답에 {@code simulated: true}가 항상 동반돼 실측인 척하지 않는다) 상수는
 * 물리적으로 그럴듯한 범위 내 임의값이다 — 출처 인용 대상이 아니다.
 *
 * <p>노이즈는 <b>{@code (deviceId, measuredAt 분)} 해시를 시드로 한 {@link Random}</b>이라
 * 재기동해도 파형이 튀지 않고, 같은 입력이면 항상 같은 값을 낸다(테스트 재현성 — contract §4.11).
 */
final class SensorSimulationProfile {

    /**
     * {@code floor}/{@code ceiling}은 물리적으로 불가능한 값이 나오지 않게 하는 클램프다 — 노이즈·층
     * 오프셋·목표 수렴이 겹치면 경계를 넘을 수 있다(리뷰 P3: 습도가 상한 없이 102%까지 나올 수 있었다).
     * 상한이 의미 없는 지표(온도 등)는 {@code POSITIVE_INFINITY}로 둔다.
     */
    private record Params(double base, double amplitude, double peakHour, double levelOffsetStep,
                           double noiseAmplitude, double floor, double ceiling) {
    }

    private static final Map<SensorMetric, Params> PARAMS = new EnumMap<>(SensorMetric.class);

    static {
        // 일교차형 — 낮 14시경 정점, 층이 높을수록(열 상승) 약간 더 따뜻함.
        PARAMS.put(SensorMetric.TEMPERATURE,
                new Params(22.0, 4.0, 14.0, 0.5, 0.3, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY));
        // 온도와 역위상(새벽에 높고 낮에 낮음), 층이 높을수록 약간 건조.
        PARAMS.put(SensorMetric.HUMIDITY, new Params(65.0, 8.0, 2.0, -1.0, 2.0, 0.0, 100.0));
        // 광합성 없는 야간·새벽에 축적 → 이른 아침 정점.
        PARAMS.put(SensorMetric.CO2,
                new Params(650.0, 150.0, 5.0, 5.0, 20.0, 0.0, Double.POSITIVE_INFINITY));
        PARAMS.put(SensorMetric.EC, new Params(2.0, 0.1, 12.0, 0.02, 0.05, 0.0, Double.POSITIVE_INFINITY));
        PARAMS.put(SensorMetric.PH, new Params(6.0, 0.1, 12.0, 0.01, 0.05, 0.0, 14.0));
        // 일사량형 — 정오 정점, 하위층일수록 광량 감소(차광).
        PARAMS.put(SensorMetric.PPFD,
                new Params(300.0, 300.0, 12.0, -10.0, 15.0, 0.0, Double.POSITIVE_INFINITY));
        PARAMS.put(SensorMetric.POWER, new Params(5.0, 2.0, 13.0, 0.1, 0.3, 0.0, Double.POSITIVE_INFINITY));
    }

    /** tick당 목표 접근 비율(contract §4.12 — "목표와 현재의 차이 × 0.2"). */
    private static final double CONVERGENCE_RATE = 0.2;

    private SensorSimulationProfile() {
    }

    /** levelNo는 1부터 시작(RackLevel 컨벤션) — 오프셋은 1층 기준 상대값이라 (levelNo - 1)을 곱한다. */
    static double simulate(SensorMetric metric, int levelNo, long deviceId, LocalDateTime measuredAt) {
        Params p = PARAMS.get(metric);
        double dayFraction = (measuredAt.getHour() * 60 + measuredAt.getMinute()) / 1440.0;
        double peakFraction = p.peakHour() / 24.0;
        double base = p.base() + p.amplitude() * Math.sin(2 * Math.PI * (dayFraction - peakFraction));
        double levelOffset = (levelNo - 1) * p.levelOffsetStep();
        double noise = deterministicNoise(deviceId, measuredAt) * p.noiseAmplitude();
        return clamp(p, base + levelOffset + noise);
    }

    /**
     * 목표값 수렴(contract §4.12 시뮬레이터 연동) — 적용된 {@code ControlSetpoint.targetValue}가
     * 일주기 sin 기저를 <b>대체</b>하고, 값은 즉시 점프하지 않고 <b>tick당 일정 비율</b>
     * ({@link #CONVERGENCE_RATE})로 목표에 근접한다. 즉시 점프시키면 시계열 그래프가 계단이 되어
     * 데모가 부자연스러워진다.
     *
     * <p>노이즈는 자연 생성과 동일한 결정적 노이즈를 그대로 얹는다 — 목표에 도달한 뒤에도 값이
     * 완전히 평평해지지 않아(실제 제어도 목표 주변에서 흔들린다) 그래프가 죽지 않는다. 직전 값에
     * 이미 노이즈가 포함돼 있지만 매 tick 목표 쪽으로 당겨지므로 누적 표류는 일어나지 않는다.
     *
     * <p>같은 함수를 <b>목표 해제 방향</b>에도 쓴다(리뷰 P3): 제어기가 꺼져 목표 수렴이 중단되면
     * {@code target} 자리에 자연 생성값을 넣어 같은 비율로 되돌아오게 한다 — 그렇지 않으면 제어기를
     * 끄는 순간 목표값에서 자연값으로 한 tick에 점프해(예: 30°C → 22°C) 계약이 피하려던 계단이
     * 반대 방향으로 생긴다.
     *
     * @param previous 직전 측정값(없으면 호출측이 자연 생성값을 넣는다 — 첫 tick)
     */
    static double converge(SensorMetric metric, double previous, double target, long deviceId,
                            LocalDateTime measuredAt) {
        Params p = PARAMS.get(metric);
        double moved = previous + (target - previous) * CONVERGENCE_RATE;
        double noise = deterministicNoise(deviceId, measuredAt) * p.noiseAmplitude();
        return clamp(p, moved + noise);
    }

    /** 지표별 물리 경계로 자른다 — 상·하한이 없는 지표는 무한대라 no-op. */
    private static double clamp(Params p, double value) {
        return Math.min(Math.max(value, p.floor()), p.ceiling());
    }

    /**
     * 시드 = {@code (deviceId, measuredAt 분)} 해시(contract §4.11) — 초 단위는 버리고 분까지만
     * 반영해 같은 분 내 재계산해도 같은 값이 나온다. epoch minute을 써서 재기동 이후에도 동일 입력이면
     * 동일 시퀀스를 재현한다(Random 인스턴스는 매 호출 새로 생성 — 상태를 들고 있지 않는다).
     */
    private static double deterministicNoise(long deviceId, LocalDateTime measuredAt) {
        long epochMinute = measuredAt.toEpochSecond(java.time.ZoneOffset.UTC) / 60;
        long seed = 31L * deviceId + epochMinute;
        Random random = new Random(seed);
        return random.nextDouble() * 2 - 1; // [-1, 1)
    }
}
