package com.smartfarm.service.scheduler;

import com.smartfarm.service.service.SensorSimulatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 가상 장비 시뮬레이터 스케줄러(contract §4.11, 이슈 #90) — 실기기 부재를 시뮬레이션으로 한정
 * 해제(docs/STATUS.md의 "원격제어·EC/pH 실시간 제외" 결정 참고).
 *
 * <p>{@code smartfarm.simulator.enabled=false}(운영에서 실기기 연동 시)면 <b>빈 자체가 등록되지
 * 않는다</b>(handoff 요건 — "빈 폴러가 도는 게 아니라 빈 자체가 안 뜨게"). {@code matchIfMissing =
 * true}로 기본값은 활성 — 프로퍼티 자체를 생략해도 데모 목적상 시뮬레이터가 켜진 채로 기동된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartfarm.simulator.enabled", havingValue = "true", matchIfMissing = true)
public class SensorSimulatorScheduler {

    private final SensorSimulatorService sensorSimulatorService;

    @Scheduled(initialDelayString = "${sensor-simulator.tick-initial-delay:PT60S}",
            fixedDelayString = "${sensor-simulator.tick-fixed-delay:PT60S}")
    public void tick() {
        try {
            sensorSimulatorService.tick();
        } catch (Exception e) {
            // 시뮬레이터 실패가 다른 스케줄러(퍼지·폴러 등)의 공유 스레드풀에 영향을 주지 않도록
            // 예외를 삼키고 로그만 남긴다(EnvironmentSnapshotPoller와 동일 원칙).
            log.warn("센서 시뮬레이터 tick 실패(로그만): {}", e.getClass().getSimpleName(), e);
        }
    }
}
