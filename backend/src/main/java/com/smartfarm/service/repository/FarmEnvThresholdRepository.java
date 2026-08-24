package com.smartfarm.service.repository;

import com.smartfarm.service.entity.FarmEnvThreshold;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FarmEnvThresholdRepository extends JpaRepository<FarmEnvThreshold, Long> {

    Optional<FarmEnvThreshold> findByFarmId(Long farmId);

    /**
     * 알람 이벤트·웹훅 평가 대상(contract §4.6) — enabled=true 전체(웹훅 URL 유무 무관, 이슈 #116
     * 리뷰 P2-B). 예전엔 웹훅이 설정된 농장만 조회했지만(findEnabledWithWebhookConfigured),
     * 웹훅(알림 채널)과 알람 이벤트(영속 기록)는 서로 다른 관심사라 그 조회로 평가 대상을 제한하면
     * 웹훅 URL을 아직 안 넣은 농장은 알람 이벤트가 전혀 쌓이지 않는 문제가 있었다. 웹훅 발송 스킵은
     * {@code EnvThresholdWebhookNotifier#notifyBreach}가 개별 farm의 webhookUrl==null을 보고 이미
     * 내부에서 처리한다.
     *
     * <p>Farm 서브쿼리는 웹훅 조건 없이도 반드시 유지한다(이슈 #116 리뷰 회귀-B) — farmId만으로
     * {@code WHERE t.enabled = true}만 걸면 {@code @SQLRestriction("deleted_at IS NULL")}이 적용되는
     * Farm 엔티티를 아예 참조하지 않아 soft delete된 농장이 다시 평가 대상에 들어온다.
     * {@code FarmService#deleteFarm}은 Farm만 soft delete하고 {@code farm_env_thresholds} 행은
     * {@code enabled=true}인 채로 그대로 남기 때문에, 이 서브쿼리가 없으면 삭제된 농장에 매 틱
     * 쿼리가 돌고 이탈 시 alarm_events가 무한 축적되는 조용한 쓰레기 데이터가 생긴다. 이 서브쿼리로
     * Farm을 참조하면 soft delete된 농장은 자동으로 제외된다.
     */
    @Query("SELECT t FROM FarmEnvThreshold t WHERE t.enabled = true AND t.farmId IN (SELECT f.id FROM Farm f)")
    List<FarmEnvThreshold> findEnabled();
}
