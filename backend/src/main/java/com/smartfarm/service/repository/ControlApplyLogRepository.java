package com.smartfarm.service.repository;

import com.smartfarm.service.entity.ControlApplyLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ControlApplyLogRepository extends JpaRepository<ControlApplyLog, Long> {

    /**
     * 프리뷰 "최근 적용" — 존별 최신 5건. 같은 밀리초 동률에서도 순서가 흔들리지 않도록
     * id를 2차 정렬 키로 둔다(응답 결정성).
     */
    List<ControlApplyLog> findTop5ByZoneIdOrderByAppliedAtDescIdDesc(Long zoneId);

    /** 90일 보존 purge 배치 삭제(contract §4.12) — SensorReadingRepository#deleteBeforeBatch와 동일 패턴. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM control_apply_logs WHERE id IN "
            + "(SELECT id FROM control_apply_logs WHERE applied_at < :cutoff LIMIT :batchSize)",
            nativeQuery = true)
    int deleteBeforeBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
