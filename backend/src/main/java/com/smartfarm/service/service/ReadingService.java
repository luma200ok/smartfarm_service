package com.smartfarm.service.service;

import com.smartfarm.service.dto.LevelSummaryResponse;
import com.smartfarm.service.dto.ReadingMatrixResponse;
import com.smartfarm.service.dto.ReadingSeriesResponse;
import com.smartfarm.service.entity.Rack;
import com.smartfarm.service.entity.RackLevel;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.entity.SensorSource;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.RackLevelRepository;
import com.smartfarm.service.repository.RackRepository;
import com.smartfarm.service.repository.ReadingLevelAverageProjection;
import com.smartfarm.service.repository.ReadingLevelLatestProjection;
import com.smartfarm.service.repository.ReadingSeriesBucketProjection;
import com.smartfarm.service.repository.SensorReadingRepository;
import com.smartfarm.service.repository.ZoneRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 센서 측정값 조회 3종(contract §4.11, 이슈 #90) — series/latest/level-summary. 적재(시뮬레이터)는
 * {@link SensorSimulatorService}가 담당하고, 이 서비스는 조회만 한다(불변 이력이라 쓰기 경로 없음).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadingService {

    /** series 요청 metric 상한(contract §4.11 — 초과 시 C001). */
    static final int MAX_SERIES_METRICS = 4;

    /** scope 필터 없이 "전체 이력에 SIMULATED가 있었는가"를 물을 때 쓰는 하한(모든 TIMESTAMP보다 이전). */
    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final FarmAccessGuard farmAccessGuard;
    private final ZoneRepository zoneRepository;
    private final RackRepository rackRepository;
    private final RackLevelRepository rackLevelRepository;
    private final SensorReadingRepository sensorReadingRepository;

    /** scope 해석 결과 — zoneId/rackId/rackLevelId 중 하나만 값이 있거나 셋 다 null(farm 스코프). */
    private record ScopeFilter(Long zoneId, Long rackId, Long rackLevelId) {
    }

    public ReadingSeriesResponse series(Long farmId, Long userId, List<SensorMetric> metrics, String rangeParam,
                                         String scopeParam) {
        farmAccessGuard.requireMember(farmId, userId);

        if (metrics == null || metrics.isEmpty()) {
            throw new CustomException(ErrorCode.C001, "metrics는 최소 1개 이상이어야 합니다.");
        }
        if (metrics.size() > MAX_SERIES_METRICS) {
            throw new CustomException(ErrorCode.C001, "metrics는 최대 " + MAX_SERIES_METRICS + "개까지 지정할 수 있습니다.");
        }

        EnvironmentHistoryRange range = EnvironmentHistoryRange.from(rangeParam);
        ReadingScope scope = ReadingScope.parse(scopeParam);
        ScopeFilter filter = resolveScope(farmId, scope);

        LocalDateTime since = LocalDateTime.now().minus(range.window());
        // 24h(다운샘플 없음)도 같은 두 단계 집계 쿼리를 타되, 버킷을 tick 주기(60s)로 둬 "버킷=원본
        // tick"이 되게 한다 — 별도 원본 조회 경로를 두지 않고 로직을 하나로 통일(handoff 요건 5의
        // "device 간 평균" 단계는 24h에서도 동일하게 필요하기 때문).
        long bucketSeconds = range.bucket() != null ? range.bucket().getSeconds() : 60L;

        List<ReadingSeriesResponse.Series> series = new ArrayList<>();
        boolean simulated = false;
        for (SensorMetric metric : metrics) {
            List<ReadingSeriesBucketProjection> rows = sensorReadingRepository.findSeriesAggregated(
                    farmId, metric.name(), since, bucketSeconds,
                    filter.zoneId(), filter.rackId(), filter.rackLevelId());
            List<ReadingSeriesResponse.Point> points = rows.stream()
                    .map(row -> new ReadingSeriesResponse.Point(row.getBucket(), row.getValue()))
                    .toList();
            series.add(new ReadingSeriesResponse.Series(metric.name(), metric.unit(), points));
            simulated |= existsSimulated(farmId, filter, metric, since);
        }

        return new ReadingSeriesResponse(range.queryValue(), scopeParam, simulated, series);
    }

    public ReadingMatrixResponse latest(Long farmId, Long userId, SensorMetric metric, Long zoneId) {
        farmAccessGuard.requireMember(farmId, userId);
        if (zoneId != null) {
            zoneRepository.findByIdAndFarmId(zoneId, farmId).orElseThrow(() -> new CustomException(ErrorCode.R001));
        }

        List<Rack> racks = zoneId != null
                ? rackRepository.findByZoneIdOrderByDisplayOrderAscIdAsc(zoneId)
                : rackRepository.findByFarmIdOrderByDisplayOrderAscIdAsc(farmId);
        List<Long> rackIds = racks.stream().map(Rack::getId).toList();
        List<RackLevel> levels = rackIds.isEmpty()
                ? List.of()
                : rackLevelRepository.findByRackIdInOrderByLevelNoAsc(rackIds);
        Map<Long, List<RackLevel>> levelsByRackId = new LinkedHashMap<>();
        for (RackLevel level : levels) {
            levelsByRackId.computeIfAbsent(level.getRackId(), k -> new ArrayList<>()).add(level);
        }

        List<Long> levelIds = levels.stream().map(RackLevel::getId).toList();
        Map<Long, ReadingLevelLatestProjection> latestByLevelId = new LinkedHashMap<>();
        if (!levelIds.isEmpty()) {
            for (ReadingLevelLatestProjection row : sensorReadingRepository.findLatestPerLevel(
                    farmId, metric.name(), levelIds)) {
                latestByLevelId.put(row.getRackLevelId(), row);
            }
        }

        List<ReadingMatrixResponse.RackRow> rackRows = racks.stream()
                .map(rack -> toRackRow(rack, levelsByRackId.getOrDefault(rack.getId(), List.of()),
                        latestByLevelId, metric))
                .toList();

        boolean simulated = zoneId != null
                ? sensorReadingRepository.existsByZoneIdAndMetricAndSourceAndMeasuredAtGreaterThanEqual(
                        zoneId, metric, SensorSource.SIMULATED, EPOCH)
                : sensorReadingRepository.existsByFarmIdAndMetricAndSourceAndMeasuredAtGreaterThanEqual(
                        farmId, metric, SensorSource.SIMULATED, EPOCH);

        return new ReadingMatrixResponse(metric.name(), metric.unit(), simulated, rackRows);
    }

    public LevelSummaryResponse levelSummary(Long farmId, Long userId, Long rackId, String rangeParam) {
        farmAccessGuard.requireMember(farmId, userId);
        if (rackId == null) {
            throw new CustomException(ErrorCode.C001, "rackId는 필수입니다.");
        }
        Rack rack = rackRepository.findByIdAndFarmId(rackId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.R002));
        EnvironmentHistoryRange range = EnvironmentHistoryRange.from(rangeParam);
        LocalDateTime since = LocalDateTime.now().minus(range.window());

        List<RackLevel> levels = rackLevelRepository.findByRackIdOrderByLevelNoAsc(rackId);
        List<ReadingLevelAverageProjection> aggregated =
                sensorReadingRepository.findLevelSummaryAggregated(farmId, rackId, since);

        // (rackLevelId, metric name) -> average
        Map<String, Double> averageByLevelMetric = new LinkedHashMap<>();
        for (ReadingLevelAverageProjection row : aggregated) {
            averageByLevelMetric.put(row.getRackLevelId() + ":" + row.getMetric(), row.getAverage());
        }

        List<LevelSummaryResponse.LevelRow> levelRows = levels.stream()
                .map(level -> toLevelRow(level, averageByLevelMetric))
                .toList();

        boolean simulated = sensorReadingRepository.existsByFarmIdAndRackIdAndSourceAndMeasuredAtGreaterThanEqual(
                farmId, rackId, SensorSource.SIMULATED, since);

        return new LevelSummaryResponse(rack.getId(), rack.getCode(), range.queryValue(), simulated, levelRows);
    }

    private LevelSummaryResponse.LevelRow toLevelRow(RackLevel level, Map<String, Double> averageByLevelMetric) {
        List<LevelSummaryResponse.MetricCell> cells = new ArrayList<>();
        for (SensorMetric metric : SensorMetric.values()) {
            Double average = averageByLevelMetric.get(level.getId() + ":" + metric.name());
            if (average == null) {
                cells.add(new LevelSummaryResponse.MetricCell(
                        metric.name(), metric.unit(), null, null, SensorThresholds.State.IDLE.name()));
            } else {
                double deviationPercent = SensorThresholds.deviationPercent(metric, average);
                SensorThresholds.State state = SensorThresholds.stateOf(metric, average);
                cells.add(new LevelSummaryResponse.MetricCell(
                        metric.name(), metric.unit(), average, deviationPercent, state.name()));
            }
        }
        return new LevelSummaryResponse.LevelRow(level.getLevelNo(), level.getLabel(), cells);
    }

    private ReadingMatrixResponse.RackRow toRackRow(Rack rack, List<RackLevel> levels,
                                                     Map<Long, ReadingLevelLatestProjection> latestByLevelId,
                                                     SensorMetric metric) {
        List<ReadingMatrixResponse.LevelCell> cells = levels.stream()
                .map(level -> {
                    ReadingLevelLatestProjection latest = latestByLevelId.get(level.getId());
                    Double value = latest != null ? latest.getValue() : null;
                    String state = value != null
                            ? SensorThresholds.stateOf(metric, value).name()
                            : SensorThresholds.State.IDLE.name();
                    return new ReadingMatrixResponse.LevelCell(level.getLevelNo(), value, state);
                })
                .toList();
        return new ReadingMatrixResponse.RackRow(rack.getId(), rack.getCode(), cells);
    }

    /**
     * scope 파라미터를 리소스 소속 검증까지 마친 필터로 해석한다(contract §4.11 필수 요건 4 — scope도
     * path와 동일하게 소속 검증, 미소속은 404). {@code FarmAccessGuard}의 cross-tenant IDOR 차단
     * 원칙을 query 파라미터에도 그대로 적용한다.
     */
    private ScopeFilter resolveScope(Long farmId, ReadingScope scope) {
        return switch (scope.type()) {
            case FARM -> new ScopeFilter(null, null, null);
            case ZONE -> {
                zoneRepository.findByIdAndFarmId(scope.id(), farmId)
                        .orElseThrow(() -> new CustomException(ErrorCode.R001));
                yield new ScopeFilter(scope.id(), null, null);
            }
            case RACK -> {
                rackRepository.findByIdAndFarmId(scope.id(), farmId)
                        .orElseThrow(() -> new CustomException(ErrorCode.R002));
                yield new ScopeFilter(null, scope.id(), null);
            }
            case LEVEL -> {
                rackLevelRepository.findByIdAndFarmId(scope.id(), farmId)
                        .orElseThrow(() -> new CustomException(ErrorCode.R003));
                yield new ScopeFilter(null, null, scope.id());
            }
        };
    }

    private boolean existsSimulated(Long farmId, ScopeFilter filter, SensorMetric metric, LocalDateTime since) {
        if (filter.rackLevelId() != null) {
            return sensorReadingRepository.existsByRackLevelIdAndMetricAndSourceAndMeasuredAtGreaterThanEqual(
                    filter.rackLevelId(), metric, SensorSource.SIMULATED, since);
        }
        if (filter.rackId() != null) {
            return sensorReadingRepository.existsByRackIdAndMetricAndSourceAndMeasuredAtGreaterThanEqual(
                    filter.rackId(), metric, SensorSource.SIMULATED, since);
        }
        if (filter.zoneId() != null) {
            return sensorReadingRepository.existsByZoneIdAndMetricAndSourceAndMeasuredAtGreaterThanEqual(
                    filter.zoneId(), metric, SensorSource.SIMULATED, since);
        }
        return sensorReadingRepository.existsByFarmIdAndMetricAndSourceAndMeasuredAtGreaterThanEqual(
                farmId, metric, SensorSource.SIMULATED, since);
    }
}
