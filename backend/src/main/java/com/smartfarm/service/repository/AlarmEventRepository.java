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

    /**
     * stats 화면 밖에서도 쓰일 수 있는 범용 조회 — 필요 시 엔티티 그대로 쓴다. stats(집계 전용)는
     * 전량 로딩을 피하려고 {@link #countBySeverityAfter}/{@link #avgAcknowledgeMinutesAfter}로
     * DB 집계한다(이슈 #116 리뷰 P2-C).
     */
    List<AlarmEvent> findByFarmIdAndOccurredAtAfter(Long farmId, LocalDateTime since);

    /**
     * stats — severity별 건수를 DB에서 집계한다(이슈 #116 리뷰 P2-C, 엔티티 전량 로딩 회피).
     * 네이티브 쿼리 컬럼 별칭을 프로젝션 getter 프로퍼티명과 동일하게 큰따옴표로 고정한다
     * (EnvSnapshotBucketProjection 선례와 동일 원칙 — camelCase 변환 추측에 기대지 않음).
     */
    @Query(value = "SELECT severity AS \"severity\", COUNT(*) AS \"eventCount\" FROM alarm_events "
            + "WHERE farm_id = :farmId AND occurred_at > :since GROUP BY severity",
            nativeQuery = true)
    List<AlarmSeverityCountProjection> countBySeverityAfter(@Param("farmId") Long farmId,
                                                             @Param("since") LocalDateTime since);

    /**
     * stats — 평균 확인 소요시간(분, occurredAt→acknowledgedAt). acknowledgedAt이 없는(미확인)
     * 이벤트는 제외. 대상 이벤트가 하나도 없으면 SQL AVG는 NULL을 반환하고 그대로 null에 매핑된다.
     */
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (acknowledged_at - occurred_at)) / 60.0) FROM alarm_events "
            + "WHERE farm_id = :farmId AND occurred_at > :since AND acknowledged_at IS NOT NULL",
            nativeQuery = true)
    Double avgAcknowledgeMinutesAfter(@Param("farmId") Long farmId, @Param("since") LocalDateTime since);

    /** TopBar 배지용 경량 카운트 — 엔티티 로드 없이 개수만 조회. */
    long countByFarmIdAndStatus(Long farmId, AlarmEventStatus status);

    /**
     * 홈 대시보드 카드(이슈 #139) — 여러 농장의 미확인 알람을 severity별로 배치 집계한다(N+1 방지,
     * farm 수와 무관하게 쿼리 1개). status 배지(CRITICAL/WARNING/NORMAL) 파생과
     * unacknowledgedAlarmCount 계산 둘 다 이 결과 하나로 충분해 별도 카운트 쿼리를 두지 않는다.
     */
    @Query("SELECT e.farmId AS farmId, e.severity AS severity, COUNT(e) AS eventCount "
            + "FROM AlarmEvent e WHERE e.farmId IN :farmIds AND e.status = :status "
            + "GROUP BY e.farmId, e.severity")
    List<AlarmSeverityFarmCountProjection> countBySeverityForFarmIds(@Param("farmIds") List<Long> farmIds,
                                                                      @Param("status") AlarmEventStatus status);

    /**
     * 홈 대시보드 카드 하단 한 줄 요약(이슈 #139) — 농장별 가장 최근 알람 이벤트(상태 무관,
     * occurredAt 최신) 메시지를 한 번에 조회한다. Postgres {@code DISTINCT ON}으로 그룹별 최신 1행을
     * 배치로 뽑는다({@link SensorReadingRepository#findLatestValueByDeviceIds}와 동일 패턴).
     */
    @Query(value = "SELECT DISTINCT ON (farm_id) farm_id AS \"farmId\", message AS \"message\" "
            + "FROM alarm_events WHERE farm_id IN (:farmIds) "
            + "ORDER BY farm_id, occurred_at DESC, id DESC", nativeQuery = true)
    List<FarmLatestAlarmProjection> findLatestMessageByFarmIds(@Param("farmIds") List<Long> farmIds);
}
