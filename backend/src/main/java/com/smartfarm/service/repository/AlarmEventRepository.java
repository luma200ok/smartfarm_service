package com.smartfarm.service.repository;

import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmEventStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlarmEventRepository extends JpaRepository<AlarmEvent, Long>, AlarmEventRepositoryCustom {

    /** farm 스코프 필수 — id 단독 조회 금지(cross-tenant IDOR 차단, 다른 도메인과 동일 원칙). */
    Optional<AlarmEvent> findByIdAndFarmId(Long id, Long farmId);

    /** 전체 확인 처리(acknowledge-all) 대상. */
    List<AlarmEvent> findByFarmIdAndStatus(Long farmId, AlarmEventStatus status);

    /**
     * 멱등성 판정(이슈 #116) — 같은 farm×metricKey 조합의 미해결(RESOLVED 아님) 이벤트. 브리치
     * 재감지 시 신규 생성 스킵, 정상 복귀 감지 시 자동 해소 대상 조회에 쓰인다.
     */
    @Query("SELECT e FROM AlarmEvent e WHERE e.farmId = :farmId AND e.metricKey = :metricKey "
            + "AND e.status <> com.smartfarm.service.entity.AlarmEventStatus.RESOLVED")
    Optional<AlarmEvent> findOpenEventByFarmAndMetric(@Param("farmId") Long farmId,
                                                       @Param("metricKey") String metricKey);

    /** 통계(stats) 대상 — 최근 N일 내 발생한 이벤트 전체(집계는 서비스 계층에서 메모리 연산). */
    List<AlarmEvent> findByFarmIdAndOccurredAtAfter(Long farmId, LocalDateTime since);

    /** TopBar 배지용 경량 카운트 — 엔티티 로드 없이 개수만 조회. */
    long countByFarmIdAndStatus(Long farmId, AlarmEventStatus status);
}
