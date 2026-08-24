package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.IntegrationTestSupport;
import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmEventStatus;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.repository.AlarmEventRepository;
import com.smartfarm.service.repository.FarmRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AlarmEventService} 시스템 훅(recordBreach/autoResolveIfOpen) 통합 테스트(이슈 #116) —
 * 실제 DB(partial unique index 포함)로 멱등성·자동 해소를 검증한다. EnvSnapshotIngestServiceIntegrationTest
 * 선례와 동일하게 클래스 레벨 @Transactional로 테스트 간 격리한다.
 */
@Transactional
class AlarmEventServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AlarmEventService alarmEventService;

    @Autowired
    private AlarmEventRepository alarmEventRepository;

    @Autowired
    private FarmRepository farmRepository;

    private Long createFarmId() {
        Farm farm = farmRepository.save(Farm.builder().name("테스트농장").cropType(CropType.TOMATO).build());
        return farm.getId();
    }

    @Test
    @DisplayName("동일 farm×metricKey 조합으로 연속 브리치를 기록해도 이벤트는 1건만 생성된다(멱등)")
    void recordBreachIsIdempotentForOpenEvent() {
        Long farmId = createFarmId();

        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_HIGH", "1차 이탈", LocalDateTime.now(), null);
        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_HIGH", "2차 이탈(연속 틱)", LocalDateTime.now(), null);
        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_HIGH", "3차 이탈(연속 틱)", LocalDateTime.now(), null);

        List<AlarmEvent> events = alarmEventRepository.findByFarmIdAndOccurredAtAfter(farmId,
                LocalDateTime.now().minusDays(1));
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getMessage()).isEqualTo("1차 이탈");
        assertThat(events.get(0).getStatus()).isEqualTo(AlarmEventStatus.UNACKNOWLEDGED);
    }

    @Test
    @DisplayName("서로 다른 metricKey는 각각 독립된 이벤트로 생성된다")
    void recordBreachCreatesSeparateEventsForDifferentMetricKeys() {
        Long farmId = createFarmId();

        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_HIGH", "온도 상한 초과", LocalDateTime.now(), null);
        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_LOW", "온도 하한 미달", LocalDateTime.now(), null);

        List<AlarmEvent> events = alarmEventRepository.findByFarmIdAndOccurredAtAfter(farmId,
                LocalDateTime.now().minusDays(1));
        assertThat(events).hasSize(2);
    }

    @Test
    @DisplayName("정상 복귀 감지 시 열린 이벤트가 자동으로 RESOLVED 전이되고 resolvedBy는 null이다")
    void autoResolveIfOpenResolvesOpenEvent() {
        Long farmId = createFarmId();
        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_HIGH", "온도 상한 초과", LocalDateTime.now(), null);

        alarmEventService.autoResolveIfOpen(farmId, "INDOOR_TEMP_HIGH");

        List<AlarmEvent> events = alarmEventRepository.findByFarmIdAndOccurredAtAfter(farmId,
                LocalDateTime.now().minusDays(1));
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getStatus()).isEqualTo(AlarmEventStatus.RESOLVED);
        assertThat(events.get(0).getResolvedBy()).isNull();
        assertThat(events.get(0).getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("자동 해소 후 같은 조합의 새 브리치는 새 이벤트로 생성된다(재발동)")
    void newBreachAfterAutoResolveCreatesNewEvent() {
        Long farmId = createFarmId();
        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_HIGH", "1차 이탈", LocalDateTime.now(), null);
        alarmEventService.autoResolveIfOpen(farmId, "INDOOR_TEMP_HIGH");

        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_HIGH", "2차 이탈(재발동)", LocalDateTime.now(), null);

        List<AlarmEvent> events = alarmEventRepository.findByFarmIdAndOccurredAtAfter(farmId,
                LocalDateTime.now().minusDays(1));
        assertThat(events).hasSize(2);
        long openCount = events.stream().filter(e -> e.getStatus() != AlarmEventStatus.RESOLVED).count();
        assertThat(openCount).isEqualTo(1);
    }

    @Test
    @DisplayName("열린 이벤트가 없는 조합에 autoResolveIfOpen을 호출해도 아무 일도 일어나지 않는다(no-op)")
    void autoResolveIfOpenIsNoOpWhenNoOpenEvent() {
        Long farmId = createFarmId();

        alarmEventService.autoResolveIfOpen(farmId, "INDOOR_TEMP_HIGH");

        List<AlarmEvent> events = alarmEventRepository.findByFarmIdAndOccurredAtAfter(farmId,
                LocalDateTime.now().minusDays(1));
        assertThat(events).isEmpty();
    }
}
