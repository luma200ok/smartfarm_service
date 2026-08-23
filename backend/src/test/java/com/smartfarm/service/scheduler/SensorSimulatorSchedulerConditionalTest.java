package com.smartfarm.service.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/**
 * {@code smartfarm.simulator.enabled=false}일 때 {@link SensorSimulatorScheduler} 빈 자체가
 * 등록되지 않는지 검증한다(contract §4.11, handoff 요건 — "빈 폴러가 도는 게 아니라 빈 자체가
 * 안 뜨게"). 다른 통합 테스트와 프로퍼티가 달라 별도 컨텍스트로 기동된다.
 */
@TestPropertySource(properties = "smartfarm.simulator.enabled=false")
class SensorSimulatorSchedulerConditionalTest extends IntegrationTestSupport {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("simulator.enabled=false면 SensorSimulatorScheduler 빈이 등록되지 않는다")
    void schedulerBeanIsAbsentWhenDisabled() {
        assertThat(applicationContext.getBeanNamesForType(SensorSimulatorScheduler.class)).isEmpty();
    }
}
