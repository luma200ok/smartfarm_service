package com.smartfarm.service.repository;

import com.smartfarm.service.entity.AlarmRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AlarmRuleRepository extends JpaRepository<AlarmRule, Long> {

    /**
     * 평가 엔진의 평가 대상(이슈 #118) — {@code EnvThresholdAlertService}가 매 틱 호출한다.
     * 규칙 id 오름차순으로 고정해 평가 순서를 결정적으로 만든다(테스트 재현성).
     *
     * <p>⚠️ <b>Farm 서브쿼리는 반드시 유지한다</b>(이슈 #116 리뷰 회귀-B가 이 자리에서 났고, #118은
     * 그 불변식을 {@code farm_env_thresholds}에서 {@code alarm_rules}로 그대로 옮겨온 것이다).
     * {@code WHERE r.enabled = true}만 걸면 {@code @SQLRestriction("deleted_at IS NULL")}이 적용되는
     * {@link com.smartfarm.service.entity.Farm} 엔티티를 아예 참조하지 않아 <b>soft delete된 농장이
     * 다시 평가 대상에 들어온다</b>. {@code FarmService#deleteFarm}은 Farm만 soft delete하고
     * {@code alarm_rules} 행은 {@code enabled=true}인 채로 그대로 남기 때문에(규칙 테이블에는 soft
     * delete가 없다), 이 서브쿼리가 없으면 삭제된 농장에 매 틱 쿼리가 돌고 이탈 시 alarm_events가
     * 무한 축적되는 조용한 쓰레기 데이터가 생긴다. 이 서브쿼리로 Farm을 참조하면 soft delete된
     * 농장은 자동으로 제외된다.
     *
     * <p>(이 필터가 실제 SQL에서 동작하는지는 {@code AlarmRuleRepositoryIntegrationTest}가 실제
     * Postgres로 검증한다 — 단위 테스트는 이 리포지토리를 목킹하므로 원리적으로 검증할 수 없다.)
     */
    @Query("SELECT r FROM AlarmRule r WHERE r.enabled = true AND r.farmId IN (SELECT f.id FROM Farm f) "
            + "ORDER BY r.id ASC")
    List<AlarmRule> findEnabled();

    /** 목록 조회(GET /alarm-rules) — 농장 스코프 필수. */
    List<AlarmRule> findByFarmIdOrderByIdAsc(Long farmId);

    /** farm 스코프 필수 — ruleId 단독 조회 금지(cross-tenant IDOR 차단, ZoneRepository와 동일 원칙) */
    Optional<AlarmRule> findByIdAndFarmId(Long id, Long farmId);

    /** 농장당 규칙 개수 상한(ALR002) 판정용. */
    long countByFarmId(Long farmId);

    /** 파생 규칙 동기화(§4.6 PUT /env-thresholds) — 그 설정 행이 만든 규칙 전량. */
    List<AlarmRule> findByThresholdId(Long thresholdId);
}
