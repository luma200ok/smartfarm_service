package com.smartfarm.service.repository;

import com.smartfarm.service.entity.FarmEnvThreshold;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 농장별 환경 임계치 설정 저장소(contract §4.6) — {@code GET/PUT /env-thresholds}의 저장소로
 * 계속 쓰인다(하위호환).
 *
 * <p>⚠️ <b>평가 대상 조회({@code findEnabled})는 이 레포에서 {@code AlarmRuleRepository#findEnabled}로
 * 옮겼다</b>(이슈 #118) — 평가 엔진이 {@code alarm_rules}를 보게 됐기 때문이다. 그 쿼리가 겸하던
 * 불변식(Farm 서브쿼리로 soft delete 농장 제외 — 이슈 #116 리뷰 회귀-B)은 옮겨간 쿼리에 그대로
 * 이식했고, 그것을 실제 Postgres로 지키는 회귀 테스트도
 * {@code FarmEnvThresholdRepositoryIntegrationTest} → {@code AlarmRuleRepositoryIntegrationTest}로
 * 함께 옮겼다. 이 레포에 그 쿼리를 남겨두면 아무도 호출하지 않는 dead code가 되고, 불변식이 두 곳에
 * 흩어져 어느 쪽이 진짜 평가 경로인지 흐려진다.
 */
public interface FarmEnvThresholdRepository extends JpaRepository<FarmEnvThreshold, Long> {

    Optional<FarmEnvThreshold> findByFarmId(Long farmId);
}
