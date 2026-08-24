package com.smartfarm.service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.entity.FarmEnvThreshold;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link FarmEnvThresholdRepository#findEnabled} JPQL 레벨 필터 검증(이슈 #116 리뷰 회귀-B) —
 * {@link EnvThresholdAlertServiceUnitTest}는 이 리포지토리를 Mockito로 목킹하므로 실제 쿼리가
 * Farm 서브쿼리(soft delete 필터)를 올바르게 거는지는 원리적으로 검증할 수 없다. 실제 Postgres로
 * 이 필터를 직접 확인한다(SensorReadingRepositoryTest 선례와 동일 스타일).
 */
@Transactional
class FarmEnvThresholdRepositoryIntegrationTest extends FarmTestSupport {

    @Autowired
    private FarmEnvThresholdRepository farmEnvThresholdRepository;

    @Autowired
    private FarmRepository farmRepository;

    @Test
    @DisplayName("회귀-B: soft delete된 농장의 임계치는 findEnabled() 대상에서 제외된다"
            + "(Farm 서브쿼리 없이 enabled=true만 걸면 FarmService.deleteFarm으로 soft delete된 "
            + "농장도 farm_env_thresholds 행이 enabled=true로 남아 있어 평가 대상에 다시 들어온다)")
    void findEnabledExcludesSoftDeletedFarm() throws Exception {
        String ownerToken = signupAndLogin("임계치농부-소프트삭제");
        long farmId = createFarm(ownerToken, "소프트삭제농장");
        farmEnvThresholdRepository.save(FarmEnvThreshold.builder()
                .farmId(farmId)
                .enabled(true)
                .indoorTempMin(20.0)
                .indoorTempMax(30.0)
                .build());

        // 삭제 전에는 평가 대상에 포함된다(사전 확인).
        assertThat(farmEnvThresholdRepository.findEnabled())
                .anyMatch(t -> t.getFarmId().equals(farmId));

        // FarmService.deleteFarm과 동일한 경로 — Farm만 soft delete하고 farm_env_thresholds 행은
        // 그대로 둔다(@SQLDelete → deleted_at 갱신, enabled=true 유지).
        Farm farm = farmRepository.findById(farmId).orElseThrow();
        farmRepository.delete(farm);

        assertThat(farmEnvThresholdRepository.findEnabled())
                .noneMatch(t -> t.getFarmId().equals(farmId));
    }
}
