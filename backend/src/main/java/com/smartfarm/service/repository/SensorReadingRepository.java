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
     *
     * <p>⚠️ {@code rack_levels}를 <b>LEFT JOIN</b>하고 층이 soft delete된 행만 제외한다(사이클 2
     * 리뷰 P2-3) — soft delete된 층(§4.10 "측정 이력만 있으면 층 soft delete + 이력 보존")의
     * 과거 이력이 집계에 계속 섞이는 문제를 막는다. {@code scope=farm}이 가장 크게 영향받지만
     * (리뷰가 지적한 지점), zone/rack scope도 그 범위 안의 개별 층이 이후 삭제됐을 수 있어
     * 동일하게 적용한다 — level scope는 대상 자체가 {@code resolveScope}에서 이미 활성 검증을
     * 거치므로 이 조건이 no-op이다. INNER JOIN이 아니라 LEFT JOIN인 이유: {@code rack_level_id}가
     * null인 읽기(zone/farm 단위 측정 — 랙 하위구조에 아직 배치 안 된 device)도 존재하므로,
     * INNER JOIN이면 그 정상 데이터까지 통째로 사라진다.
     */
    @Query(value = """
            WITH device_avg AS (
                SELECT sr.measured_at, sr.rack_level_id, avg(sr.value) AS v
                FROM sensor_readings sr
                LEFT JOIN rack_levels rl ON rl.id = sr.rack_level_id
                WHERE sr.farm_id = :farmId
                  AND sr.metric = :metric
                  AND sr.measured_at >= :since
                  AND (sr.rack_level_id IS NULL OR rl.deleted_at IS NULL)
                  AND (CAST(:zoneId AS BIGINT) IS NULL OR sr.zone_id = :zoneId)
                  AND (CAST(:rackId AS BIGINT) IS NULL OR sr.rack_id = :rackId)
                  AND (CAST(:rackLevelId AS BIGINT) IS NULL OR sr.rack_level_id = :rackLevelId)
                GROUP BY sr.measured_at, sr.rack_level_id
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
     *
     * <p>⚠️ 바깥 SELECT에 {@code farm_id}/{@code metric} 술어가 반드시 있어야 한다(사이클 2 리뷰
     * P1-1) — CTE는 좁혀도 값을 만드는 바깥 SELECT가 그 좁힘을 상속하지 않으면 JOIN 조건이
     * {@code (rack_level_id, measured_at)}뿐이라 같은 층·같은 틱의 <b>모든 지표</b>가 섞여
     * 평균된다(시뮬레이터가 한 device의 전 metric을 동일 분단위 {@code measuredAt}으로 쓰므로
     * 정상 운영에서 100% 발생). 값을 반환하는 쿼리에 테넌트 술어(farm_id)가 없는 것도 이 레포
     * 규칙(#89, farm 스코프 필수)의 유일한 예외였다 — 방어 깊이 차원에서도 필요.
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
            WHERE sr.farm_id = :farmId AND sr.metric = :metric
            GROUP BY sr.rack_level_id
            """, nativeQuery = true)
    List<ReadingLevelLatestProjection> findLatestPerLevel(@Param("farmId") Long farmId,
                                                           @Param("metric") String metric,
                                                           @Param("rackLevelIds") List<Long> rackLevelIds);

    /**
     * 알람 규칙 평가용 <b>스코프 단위 최신값</b>(이슈 #118) — {@link #findLatestPerLevel}과 동일한
     * 두 단계 구조(① 스코프 안의 가장 최근 tick을 찾고 ② 그 tick 안에서 device 간 평균)를 쓰되,
     * 층 축을 접고 scope 필터(zone/rack/level 중 하나 또는 전부 null=farm)를 파라미터로 받는다.
     * 기존 {@code findLatestPerLevel}은 층 id 목록을 <b>필수로</b> 받아 층 단위로만 답하므로,
     * farm/zone/rack 스코프 규칙이나 층에 매달리지 않은 센서(zone 직속 등)를 평가할 수 없다 —
     * 층 목록을 매 틱 조회해 우회하면 규칙 하나당 쿼리가 여러 개로 늘고 그 센서들은 여전히 누락된다.
     *
     * <p>⚠️ {@code findLatestPerLevel}과 같은 이유로 <b>바깥 SELECT에도 {@code farm_id}/
     * {@code metric} 술어를 반드시 둔다</b>(사이클 2 리뷰 P1-1) — CTE만 좁히고 바깥이 그 좁힘을
     * 상속하지 않으면 같은 tick의 <b>모든 지표</b>가 섞여 평균된다(시뮬레이터가 한 device의 전
     * metric을 동일 분단위 {@code measuredAt}으로 쓰므로 정상 운영에서 100% 발생).
     *
     * <p>⚠️ {@code findSeriesAggregated}와 같은 이유로 <b>soft delete된 층의 측정값은 제외</b>한다
     * (사이클 2 리뷰 P2-3) — 삭제된 층의 마지막 값이 스코프 최신값으로 잡혀 알람이 발동/유지되는
     * 것을 막는다. {@code rack_level_id}가 null인 읽기(존 직속 센서 등)도 살려야 하므로 INNER가
     * 아니라 LEFT JOIN이다.
     *
     * <p>{@code since}는 신선도 하한이다 — 이보다 오래된 값만 있으면 <b>결과가 비어</b> 호출측이
     * "관측 부재"로 판정해 평가를 건너뛴다(오래된 값으로 알람을 발동/해소하지 않기 위함).
     */
    @Query(value = """
            WITH latest_tick AS (
                SELECT max(sr.measured_at) AS at
                FROM sensor_readings sr
                LEFT JOIN rack_levels rl ON rl.id = sr.rack_level_id
                WHERE sr.farm_id = :farmId
                  AND sr.metric = :metric
                  AND sr.measured_at >= :since
                  AND (sr.rack_level_id IS NULL OR rl.deleted_at IS NULL)
                  AND (CAST(:zoneId AS BIGINT) IS NULL OR sr.zone_id = :zoneId)
                  AND (CAST(:rackId AS BIGINT) IS NULL OR sr.rack_id = :rackId)
                  AND (CAST(:rackLevelId AS BIGINT) IS NULL OR sr.rack_level_id = :rackLevelId)
            )
            SELECT avg(sr.value) AS "value", max(sr.measured_at) AS "measuredAt"
            FROM sensor_readings sr
            LEFT JOIN rack_levels rl ON rl.id = sr.rack_level_id
            JOIN latest_tick lt ON sr.measured_at = lt.at
            WHERE sr.farm_id = :farmId
              AND sr.metric = :metric
              AND (sr.rack_level_id IS NULL OR rl.deleted_at IS NULL)
              AND (CAST(:zoneId AS BIGINT) IS NULL OR sr.zone_id = :zoneId)
              AND (CAST(:rackId AS BIGINT) IS NULL OR sr.rack_id = :rackId)
              AND (CAST(:rackLevelId AS BIGINT) IS NULL OR sr.rack_level_id = :rackLevelId)
            HAVING count(*) > 0
            """, nativeQuery = true)
    List<ReadingScopeLatestProjection> findLatestInScope(@Param("farmId") Long farmId,
                                                          @Param("metric") String metric,
                                                          @Param("since") LocalDateTime since,
                                                          @Param("zoneId") Long zoneId,
                                                          @Param("rackId") Long rackId,
                                                          @Param("rackLevelId") Long rackLevelId);

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

    /**
     * 장비×지표별 <b>직전 측정값</b>(contract §4.12 시뮬레이터 연동) — 목표값 수렴이
     * "직전 값 + (목표 - 직전 값) × 비율"이라 tick마다 필요하다. {@code DISTINCT ON}(Postgres)으로
     * 그룹별 최신 1행을 한 번에 뽑는다 — 장비 수만큼 쿼리를 날리면(N+1) 1틱이 수백 쿼리가 된다.
     *
     * <p>조회 범위는 목표값이 설정된 존의 장비로 한정된다(호출측이 device id 목록을 좁혀 넘긴다) —
     * 목표값이 하나도 없으면 아예 호출하지 않는다.
     */
    @Query(value = "SELECT DISTINCT ON (sr.device_id, sr.metric) "
            + "sr.device_id AS \"deviceId\", sr.metric AS \"metric\", sr.value AS \"value\" "
            + "FROM sensor_readings sr WHERE sr.device_id IN (:deviceIds) "
            + "ORDER BY sr.device_id, sr.metric, sr.measured_at DESC",
            nativeQuery = true)
    List<ReadingLatestValueProjection> findLatestValueByDeviceIds(@Param("deviceIds") List<Long> deviceIds);

    /**
     * 홈 대시보드 카드 지표 3열(온도·습도·EC, 이슈 #139) — 여러 농장 × 여러 지표의 "농장 전체
     * 최신값"을 한 번에 구한다(N+1 방지, farm 수와 무관하게 쿼리 1개). {@link #findLatestPerLevel}과
     * 동일한 2단계 구조(① farm×metric별 가장 최근 tick을 찾고 ② 그 tick 안에서 device 간 평균을
     * 낸다)를 층 축 대신 농장 축에 적용한다 — 서로 다른 device가 조금씩 다른 시각에 보고해도 "가장
     * 최근 tick"만 골라 평균하므로 오래된 device 값이 섞이지 않는다.
     *
     * <p>⚠️ {@link #findLatestPerLevel}과 같은 이유로 바깥 SELECT에도 {@code farm_id}/{@code metric}
     * 술어를 둔다(사이클 2 리뷰 P1-1 선례) — CTE만 좁히고 바깥이 그 좁힘을 상속하지 않으면 같은
     * tick의 다른 지표까지 섞여 평균된다(시뮬레이터가 한 device의 전 metric을 동일 분단위
     * measuredAt으로 쓰므로 정상 운영에서 100% 발생).
     */
    @Query(value = """
            WITH latest_tick AS (
                SELECT farm_id, metric, max(measured_at) AS at
                FROM sensor_readings
                WHERE farm_id IN (:farmIds) AND metric IN (:metrics)
                GROUP BY farm_id, metric
            )
            SELECT sr.farm_id AS "farmId", sr.metric AS "metric",
                   avg(sr.value) AS "value", max(sr.measured_at) AS "measuredAt"
            FROM sensor_readings sr
            JOIN latest_tick lt ON sr.farm_id = lt.farm_id AND sr.metric = lt.metric AND sr.measured_at = lt.at
            WHERE sr.farm_id IN (:farmIds) AND sr.metric IN (:metrics)
            GROUP BY sr.farm_id, sr.metric
            """, nativeQuery = true)
    List<ReadingFarmLatestProjection> findLatestByFarmIdsAndMetrics(@Param("farmIds") List<Long> farmIds,
                                                                     @Param("metrics") List<String> metrics);

    /**
     * 홈 대시보드 카드 7일 미니 추이(이슈 #139) — 여러 농장의 대표 지표({@code metric}) 일별 평균을
     * 한 번에 구한다(N+1 방지, farm 수와 무관하게 쿼리 1개). {@link #findSeriesAggregated}와 동일한
     * device-평균→시간-평균 2단계를 쓰되 버킷을 달력일(1일)로 고정한다 — 카드의 미니 막대 7개가
     * 목적이라 series의 세밀한 30분/2시간 버킷은 과하고(카드 하나에 수백 포인트를 만들 이유가 없다),
     * 대표 지표 하나로 한정해 쿼리·페이로드를 가볍게 유지한다(홈 대시보드 handoff 판단 — trend7d는
     * "무거운 조회"라 다운샘플 단위를 명시적으로 낮췄다). soft delete된 층의 과거 이력은 series와
     * 동일하게 제외한다(사이클 2 리뷰 P2-3 선례).
     */
    @Query(value = """
            WITH device_avg AS (
                SELECT sr.farm_id, sr.measured_at, sr.rack_level_id, avg(sr.value) AS v
                FROM sensor_readings sr
                LEFT JOIN rack_levels rl ON rl.id = sr.rack_level_id
                WHERE sr.farm_id IN (:farmIds)
                  AND sr.metric = :metric
                  AND sr.measured_at >= :since
                  AND (sr.rack_level_id IS NULL OR rl.deleted_at IS NULL)
                GROUP BY sr.farm_id, sr.measured_at, sr.rack_level_id
            )
            SELECT farm_id AS "farmId", CAST(measured_at AS date) AS "bucketDate", avg(v) AS "value"
            FROM device_avg
            GROUP BY farm_id, CAST(measured_at AS date)
            ORDER BY farm_id, bucketDate
            """, nativeQuery = true)
    List<ReadingFarmDailyTrendProjection> findDailyTrendByFarmIds(@Param("farmIds") List<Long> farmIds,
                                                                   @Param("metric") String metric,
                                                                   @Param("since") LocalDateTime since);
}
