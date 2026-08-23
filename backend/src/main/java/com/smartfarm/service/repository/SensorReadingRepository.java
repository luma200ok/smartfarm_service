package com.smartfarm.service.repository;

import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.entity.SensorReading;
import com.smartfarm.service.entity.SensorSource;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 조회 3종(series/latest/level-summary) 전부 네이티브 쿼리다 — 다운샘플·이중 평균이 JPQL/QueryDSL로
 * 표현하기 어렵고(EnvSnapshotRepository 선례와 동일한 이유). {@code metric}/{@code source}는 enum
 * 대신 문자열(name())로 바인딩한다 — 네이티브 쿼리에서 enum 파라미터를 그대로 바인딩하면 드라이버가
 * ordinal(int)로 보낼 수 있어 이 레포 다른 네이티브 쿼리에도 없는 SpEL 트릭 대신 문자열 바인딩을
 * 택했다(호출측 서비스가 {@code metric.name()}을 넘긴다).
 */
public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    /**
     * {@code /readings/series} 다운샘플 집계(contract §4.11 "집계의 이중성 정의") — 두 단계 순서가
     * 핵심이다: ① {@code device_avg} CTE가 같은 {@code (measured_at, rack_level_id)} 내 device 간
     * 평균을 먼저 구해 "층 하나 = 값 하나"로 만들고(센서 대수가 많은 층이 가중치를 더 갖는 것을
     * 방지), ② 바깥 SELECT가 그 결과를 다운샘플 버킷(24h=60s 원본/7d=30분/30d=2시간) 단위로 다시
     * 평균한다 — 이때 층 간에도 동등 가중치가 적용된다(scope가 여러 층을 포함할 때).
     *
     * <p>scope 필터(zoneId/rackId/rackLevelId)는 셋 중 하나만 값이 있거나(zone/rack/level 스코프)
     * 셋 다 null(farm 스코프)이다 — 서비스 계층이 {@code scope} 파라미터를 해석해 셋 중 하나만
     * 채워 넘긴다. 버킷 경계는 EnvSnapshotRepository#findAggregated와 동일하게 절대 epoch 고정
     * 그리드(재현성).
     */
    @Query(value = """
            WITH device_avg AS (
                SELECT measured_at, rack_level_id, avg(value) AS v
                FROM sensor_readings
                WHERE farm_id = :farmId
                  AND metric = :metric
                  AND measured_at >= :since
                  AND (CAST(:zoneId AS BIGINT) IS NULL OR zone_id = :zoneId)
                  AND (CAST(:rackId AS BIGINT) IS NULL OR rack_id = :rackId)
                  AND (CAST(:rackLevelId AS BIGINT) IS NULL OR rack_level_id = :rackLevelId)
                GROUP BY measured_at, rack_level_id
            )
            SELECT to_timestamp(floor(extract(epoch FROM measured_at) / :bucketSeconds) * :bucketSeconds)
                       AS "bucket",
                   avg(v) AS "value"
            FROM device_avg
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<ReadingSeriesBucketProjection> findSeriesAggregated(@Param("farmId") Long farmId,
                                                              @Param("metric") String metric,
                                                              @Param("since") LocalDateTime since,
                                                              @Param("bucketSeconds") long bucketSeconds,
                                                              @Param("zoneId") Long zoneId,
                                                              @Param("rackId") Long rackId,
                                                              @Param("rackLevelId") Long rackLevelId);

    /**
     * {@code /readings/latest} 랙 도면 셀(contract §4.11) — 층별 가장 최근 tick을 찾은 뒤(같은
     * 최신 tick 내) device 간 평균을 낸다(handoff 요건 5와 동일한 device-평균 원칙, 시간축은
     * "최신 1틱"으로 고정된 특수 케이스).
     */
    @Query(value = """
            WITH latest_tick AS (
                SELECT rack_level_id, max(measured_at) AS at
                FROM sensor_readings
                WHERE farm_id = :farmId
                  AND metric = :metric
                  AND rack_level_id IN (:rackLevelIds)
                GROUP BY rack_level_id
            )
            SELECT sr.rack_level_id AS "rackLevelId",
                   avg(sr.value) AS "value",
                   max(sr.measured_at) AS "measuredAt"
            FROM sensor_readings sr
            JOIN latest_tick lt ON sr.rack_level_id = lt.rack_level_id AND sr.measured_at = lt.at
            GROUP BY sr.rack_level_id
            """, nativeQuery = true)
    List<ReadingLevelLatestProjection> findLatestPerLevel(@Param("farmId") Long farmId,
                                                           @Param("metric") String metric,
                                                           @Param("rackLevelIds") List<Long> rackLevelIds);

    /**
     * {@code /readings/level-summary} 층별×지표별 평균(contract §4.11) — series와 동일한 두 단계
     * 순서(device 간 평균 → 시간 평균)를 쓰되, 층 축은 유지한 채(rack_level_id로 GROUP BY 계속)
     * 시간축만 range 전체로 접어 층 하나당 값 하나를 낸다. metric 파라미터가 없어(계약 — rackId만
     * 필수) 7종 전체를 한 번에 집계한다.
     */
    @Query(value = """
            WITH device_avg AS (
                SELECT measured_at, rack_level_id, metric, avg(value) AS v
                FROM sensor_readings
                WHERE farm_id = :farmId
                  AND rack_id = :rackId
                  AND measured_at >= :since
                GROUP BY measured_at, rack_level_id, metric
            )
            SELECT rack_level_id AS "rackLevelId", metric AS "metric", avg(v) AS "average"
            FROM device_avg
            GROUP BY rack_level_id, metric
            """, nativeQuery = true)
    List<ReadingLevelAverageProjection> findLevelSummaryAggregated(@Param("farmId") Long farmId,
                                                                    @Param("rackId") Long rackId,
                                                                    @Param("since") LocalDateTime since);

    // 응답 simulated 플래그(contract §4.11 "조회 범위 내 source의 집계 결과") 판정용 — scope가
    // zone/rack/level/farm 중 정확히 하나로 이미 해석된 뒤 호출되므로(ReadingService), nullable
    // CAST 트릭 대신 스코프별 파생 쿼리 4종을 둔다(이 레포 다른 파생 쿼리와 동일한 평이한 형태).
    boolean existsByFarmIdAndMetricAndSourceAndMeasuredAtGreaterThanEqual(
            Long farmId, SensorMetric metric, SensorSource source, LocalDateTime since);

    boolean existsByZoneIdAndMetricAndSourceAndMeasuredAtGreaterThanEqual(
            Long zoneId, SensorMetric metric, SensorSource source, LocalDateTime since);

    boolean existsByRackIdAndMetricAndSourceAndMeasuredAtGreaterThanEqual(
            Long rackId, SensorMetric metric, SensorSource source, LocalDateTime since);

    boolean existsByRackLevelIdAndMetricAndSourceAndMeasuredAtGreaterThanEqual(
            Long rackLevelId, SensorMetric metric, SensorSource source, LocalDateTime since);

    /** level-summary(metric 없이 rackId 전체) simulated 판정용. */
    boolean existsByFarmIdAndRackIdAndSourceAndMeasuredAtGreaterThanEqual(
            Long farmId, Long rackId, SensorSource source, LocalDateTime since);

    /**
     * 90일 보존 purge 배치 삭제(contract §4.11) — EnvSnapshotRepository#deleteBeforeBatch와 동일한
     * 배치 반복 삭제 패턴(장기 락 경합 회피).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM sensor_readings WHERE id IN "
            + "(SELECT id FROM sensor_readings WHERE measured_at < :cutoff LIMIT :batchSize)",
            nativeQuery = true)
    int deleteBeforeBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
