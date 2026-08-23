package com.smartfarm.service.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * 기본 설정(application-test.yml의 {@code smartfarm.simulator.enabled=true})에서는
 * {@link SensorSimulatorScheduler} 빈이 정상 등록되는지 확인 — 조건부 등록 자체가 항상 꺼진
 * 상태로 오작동하는 회귀를 잡는다(SensorSimulatorSchedulerConditionalTest의 반대 케이스).
 * 다른 통합 테스트와 프로퍼티가 같아 컨텍스트 캐시를 공유한다(추가 기동 비용 없음).
 */
class SensorSimulatorSchedulerEnabledTest extends IntegrationTestSupport {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("simulator.enabled=true(기본값)면 SensorSimulatorScheduler 빈이 등록된다")
    void schedulerBeanIsPresentWhenEnabled() {
        assertThat(applicationContext.getBeanNamesForType(SensorSimulatorScheduler.class)).hasSize(1);
    }
}
