package com.smartfarm.service.repository;

import com.smartfarm.service.entity.EnvSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnvSnapshotRepository extends JpaRepository<EnvSnapshot, Long> {

    /** 폴러 중복 skip 판정(contract §4.6) — 직전 적재 행의 captured_at과 신규 응답의 updated_at을 비교. */
    Optional<EnvSnapshot> findFirstByOrderByCapturedAtDesc();

    /** 24h range — 다운샘플 없이 원본 그대로(contract §4.6). */
    List<EnvSnapshot> findByCapturedAtGreaterThanEqualOrderByCapturedAtAsc(LocalDateTime since);

    /**
     * 7d/30d range 다운샘플 집계(contract §4.6) — bucketSeconds 단위로 시간 버킷을 나눠 평균.
     * 버킷 경계는 절대 epoch(1970-01-01 UTC) 기준 고정 그리드라, 조회 시점이 달라져도 같은 원본
     * 행들은 항상 같은 버킷으로 묶인다(정합성). 빈 버킷은 GROUP BY 특성상 자연히 결과에서 생략된다.
     */
    @Query(value = """
            SELECT to_timestamp(floor(extract(epoch FROM captured_at) / :bucketSeconds) * :bucketSeconds)
                       AS "bucket",
                   avg(outdoor_temp) AS "outdoorTemp",
                   avg(outdoor_humidity) AS "outdoorHumidity",
                   avg(indoor_temp) AS "indoorTemp",
                   avg(indoor_humidity) AS "indoorHumidity"
            FROM env_snapshots
            WHERE captured_at >= :since
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<EnvSnapshotBucketProjection> findAggregated(@Param("since") LocalDateTime since,
                                                       @Param("bucketSeconds") long bucketSeconds);

    /**
     * 90일 보존 퍼지 배치 삭제(contract §4.6) — RefreshTokenPurgeService/Scheduler 선례와 동일하게
     * 배치 단위 반복 삭제로 장기 락 경합을 피한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM env_snapshots WHERE id IN "
            + "(SELECT id FROM env_snapshots WHERE captured_at < :cutoff LIMIT :batchSize)",
            nativeQuery = true)
    int deleteBeforeBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
