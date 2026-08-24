package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.AlarmComparator;
import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmEventStatus;
import com.smartfarm.service.entity.AlarmRule;
import com.smartfarm.service.entity.AlarmRuleSource;
import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.repository.AlarmEventRepository;
import com.smartfarm.service.repository.AlarmRuleRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 스코프 대상(존/랙/층) soft delete 시 규칙 자기방어 통합 테스트(이슈 #118 리뷰 P2-3).
 *
 * <p>{@code alarm_rules.scope_id}는 다형 참조라 FK가 없고, {@code RackService}는 알람 규칙을
 * 모른다(이 사이클에서 그쪽은 건드리지 않는 것이 설계 결정이다). 따라서 랙을 지워도 규칙은
 * {@code enabled=true}로 남아 매 틱 평가된다 — 평가 시점의 자기방어가 실제로 도는지 실제 DB로
 * 확인한다. 특히 {@code DEVICE_HEARTBEAT}이었다면 빈 스코프가 "관측 부재"로 판정돼 자동 해소가
 * 영영 돌지 않으므로, 그 시점 열린 알람이 <b>영구 미해결</b>이 된다.
 */
class AlarmRuleScopeDeletionIntegrationTest extends FarmTestSupport {

    @Autowired
    private AlarmRuleRepository alarmRuleRepository;

    @Autowired
    private AlarmEventRepository alarmEventRepository;

    @Autowired
    private EnvThresholdAlertService envThresholdAlertService;

    @Test
    @DisplayName("P2-3: 랙이 soft delete되면 그 랙 스코프 규칙의 열린 알람이 다음 평가에서 자동 "
            + "해소되고, 규칙은 이후 평가에서 건너뛴다(규칙 행 자체는 남는다)")
    void rackSoftDeleteResolvesOpenAlarmOfRackScopedRule() throws Exception {
        String token = signupAndLogin("스코프삭제농부");
        long farmId = createFarm(token, "스코프삭제농장");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "B3", 3);

        AlarmRule rule = alarmRuleRepository.save(AlarmRule.builder()
                .farmId(farmId)
                .name("B3랙 EC 경보")
                .enabled(true)
                .source(AlarmRuleSource.SENSOR_READING)
                .metric(SensorMetric.EC.name())
                .comparator(AlarmComparator.GT)
                .thresholdValue(2.8)
                .durationSeconds(60)
                .severity(AlarmSeverity.CRITICAL)
                .scopeType(AlarmScopeType.RACK)
                .scopeId(rackId)
                .build());
        AlarmEvent open = alarmEventRepository.save(AlarmEvent.builder()
                .farmId(farmId)
                .severity(AlarmSeverity.CRITICAL)
                .sourceType(AlarmSourceType.SENSOR_THRESHOLD)
                .metricKey(rule.metricKey())
                .message("B3랙 EC 상한 초과")
                .occurredAt(LocalDateTime.now())
                .ruleId(rule.getId())
                .scopeType(AlarmScopeType.RACK)
                .scopeId(rackId)
                .build());

        // 랙 삭제(하위 층 함께 soft delete) — 규칙과 알람은 그대로 남는다.
        mockMvc.perform(delete("/api/farms/" + farmId + "/racks/" + rackId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(alarmRuleRepository.findById(rule.getId())).isPresent();

        envThresholdAlertService.resetState(); // 다른 테스트가 남긴 인메모리 상태 격리
        envThresholdAlertService.evaluate(null);

        AlarmEvent after = alarmEventRepository.findById(open.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(AlarmEventStatus.RESOLVED);
        assertThat(after.getResolvedBy()).isNull(); // 시스템 자동 해소

        // 이후 평가에서도 조용히 건너뛴다(새 알람을 만들지 않는다).
        envThresholdAlertService.evaluate(null);
        assertThat(alarmEventRepository.findOpenEventByFarmAndMetric(farmId, rule.metricKey())).isEmpty();
    }
}
