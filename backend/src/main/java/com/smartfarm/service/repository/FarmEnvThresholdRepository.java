package com.smartfarm.service.repository;

import com.smartfarm.service.entity.FarmEnvThreshold;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FarmEnvThresholdRepository extends JpaRepository<FarmEnvThreshold, Long> {

    Optional<FarmEnvThreshold> findByFarmId(Long farmId);

    /**
     * 임계치 평가 대상(contract §4.6) — enabled=true이고 웹훅이 설정된 농장만. Farm 서브쿼리는
     * {@code @SQLRestriction("deleted_at IS NULL")}이 자동 적용돼 soft delete된 농장은 제외된다.
     */
    @Query("SELECT t FROM FarmEnvThreshold t WHERE t.enabled = true "
            + "AND t.farmId IN (SELECT f.id FROM Farm f WHERE f.webhookUrl IS NOT NULL)")
    List<FarmEnvThreshold> findEnabledWithWebhookConfigured();
}
