package com.smartfarm.service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.AlarmComparator;
import com.smartfarm.service.entity.AlarmRule;
import com.smartfarm.service.entity.AlarmRuleSource;
import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.Farm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AlarmRuleRepository#findEnabled} JPQL 레벨 필터 검증(이슈 #116 리뷰 회귀-B의 #118 이식본).
 *
 * <p>이 테스트는 원래 {@code FarmEnvThresholdRepositoryIntegrationTest}였다 — #118에서 평가 대상
 * 조회가 {@code farm_env_thresholds}에서 {@code alarm_rules}로 옮겨가면서, 그 쿼리가 겸하고 있던
 * soft delete 필터 불변식과 이를 지키는 회귀 테스트도 함께 옮겼다. (쿼리를 교체하면서 필터를 조용히
 * 흘리는 것이 바로 회귀-B의 실패 양태였다.)
 *
 * <p>{@code EnvThresholdAlertServiceUnitTest}는 이 리포지토리를 Mockito로 목킹하므로 실제 쿼리가
 * Farm 서브쿼리를 올바르게 거는지는 원리적으로 검증할 수 없다. 실제 Postgres로 직접 확인한다
 * (SensorReadingRepositoryTest 선례와 동일 스타일).
 */
@Transactional
class AlarmRuleRepositoryIntegrationTest extends FarmTestSupport {

    @Autowired
    private AlarmRuleRepository alarmRuleRepository;

    @Autowired
    private FarmRepository farmRepository;

    @Test
    @DisplayName("회귀-B: soft delete된 농장의 알람 규칙은 findEnabled() 대상에서 제외된다"
            + "(Farm 서브쿼리 없이 enabled=true만 걸면 FarmService.deleteFarm으로 soft delete된 "
            + "농장도 alarm_rules 행이 enabled=true로 남아 있어 평가 대상에 다시 들어온다 — "
            + "alarm_rules에는 soft delete 컬럼 자체가 없다)")
    void findEnabledExcludesSoftDeletedFarm() throws Exception {
        String ownerToken = signupAndLogin("규칙농부-소프트삭제");
        long farmId = createFarm(ownerToken, "소프트삭제농장");
        alarmRuleRepository.save(AlarmRule.builder()
                .farmId(farmId)
                .name("실내 온도 상한")
                .enabled(true)
                .source(AlarmRuleSource.ENV_SNAPSHOT)
                .metric("INDOOR_TEMP")
                .comparator(AlarmComparator.GT)
                .thresholdValue(30.0)
                .durationSeconds(120)
                .severity(AlarmSeverity.WARNING)
                .scopeType(AlarmScopeType.FARM)
                .build());

        // 삭제 전에는 평가 대상에 포함된다(사전 확인).
        assertThat(alarmRuleRepository.findEnabled())
                .anyMatch(rule -> rule.getFarmId().equals(farmId));

        // FarmService.deleteFarm과 동일한 경로 — Farm만 soft delete하고 alarm_rules 행은 그대로 둔다
        // (@SQLDelete → deleted_at 갱신, 규칙은 enabled=true 유지).
        Farm farm = farmRepository.findById(farmId).orElseThrow();
        farmRepository.delete(farm);

        assertThat(alarmRuleRepository.findEnabled())
                .noneMatch(rule -> rule.getFarmId().equals(farmId));
    }

    @Test
    @DisplayName("enabled=false 규칙은 findEnabled() 대상에서 제외된다")
    void findEnabledExcludesDisabledRules() throws Exception {
        String ownerToken = signupAndLogin("규칙농부-비활성");
        long farmId = createFarm(ownerToken, "비활성규칙농장");
        AlarmRule disabled = alarmRuleRepository.save(AlarmRule.builder()
                .farmId(farmId)
                .name("비활성 규칙")
                .enabled(false)
                .source(AlarmRuleSource.ENV_SNAPSHOT)
                .metric("INDOOR_HUMIDITY")
                .comparator(AlarmComparator.LT)
                .thresholdValue(40.0)
                .durationSeconds(120)
                .severity(AlarmSeverity.WARNING)
                .scopeType(AlarmScopeType.FARM)
                .build());

        assertThat(alarmRuleRepository.findEnabled())
                .noneMatch(rule -> rule.getId().equals(disabled.getId()));
    }
}
