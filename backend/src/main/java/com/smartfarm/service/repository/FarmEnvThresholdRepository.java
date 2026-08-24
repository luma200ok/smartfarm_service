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
     */
    @Query("SELECT t FROM FarmEnvThreshold t WHERE t.enabled = true")
    List<FarmEnvThreshold> findEnabled();
}
