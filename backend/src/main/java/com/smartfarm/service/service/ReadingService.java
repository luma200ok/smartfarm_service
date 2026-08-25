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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * CSV 내보내기 행 수 상한(이슈 #126, 초과 시 SA003) — {@link #exportCsv}가 {@link #series}를
     * 그대로 재사용하므로 구조적으로 <b>metric당 최대 버킷 수(24h 원본 1440) × MAX_SERIES_METRICS
     * (4) = 5760</b>을 넘을 수 없다(contract §4.11 "응답 크기 상한"의 5760과 동일 계산). 그럼에도
     * 명시적으로 검사하는 이유는 ① 방어선을 코드에 남겨 회귀를 잡고, ② 다운샘플 버킷을 더 촘촘하게
     * 바꾸는(예: 24h를 30초 버킷으로) 미래 변경이 이 상한을 조용히 깨지 않게 하기 위함이다.
     */
    static final int MAX_EXPORT_ROWS = 5760;

    private static final DateTimeFormatter CSV_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter EXPORT_FILENAME_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** UTF-8 BOM(EF BB BF) — {@link #exportCsv} 한글 엑셀 호환 근거는 그 메서드 주석 참고. */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** scope 필터 없이 "전체 이력에 SIMULATED가 있었는가"를 물을 때 쓰는 하한(모든 TIMESTAMP보다 이전). */
    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final FarmAccessGuard farmAccessGuard;
    private final ZoneRepository zoneRepository;
    private final RackRepository rackRepository;
    private final RackLevelRepository rackLevelRepository;
    private final SensorReadingRepository sensorReadingRepository;

    /**
     * 신선도 상한 산정용 tick 주기(contract §4.11 사이클 2 리뷰 P2-2) — {@code /readings/latest}
     * 최신값이 이 주기의 5배보다 오래됐으면 IDLE로 떨어뜨린다. 시뮬레이터의 실제 tick 주기
     * ({@code sensor-simulator.tick-fixed-delay})와 같은 값을 참조해야 "정상 운영 중 몇 틱을
     * 놓치면 신선도 상한을 넘는가"가 시뮬레이터 설정과 항상 일치한다.
     */
    @Value("${sensor-simulator.tick-fixed-delay:PT60S}")
    private Duration tickInterval;

    /** 신선도 상한 배수 — tick 주기 × 5(contract §4.11). */
    private static final int FRESHNESS_TICK_MULTIPLIER = 5;

    /** scope 해석 결과 — zoneId/rackId/rackLevelId 중 하나만 값이 있거나 셋 다 null(farm 스코프). */
    private record ScopeFilter(Long zoneId, Long rackId, Long rackLevelId) {
    }

    public ReadingSeriesResponse series(Long farmId, Long userId, List<SensorMetric> metrics, String rangeParam,
                                         String scopeParam) {
        farmAccessGuard.requireMember(farmId, userId);

        if (metrics == null || metrics.isEmpty()) {
            throw new CustomException(ErrorCode.C001, "metrics는 최소 1개 이상이어야 합니다.");
        }
        // 중복 제거(사이클 2 리뷰 P2-5) — size 검사 전에 걸어야 "TEMPERATURE,TEMPERATURE,TEMPERATURE,
        // TEMPERATURE"처럼 중복으로 4개를 채워 상한을 우회하면서 동일 최고비용 쿼리를 반복 실행시키는
        // 걸 막는다. LinkedHashSet으로 순서는 보존.
        List<SensorMetric> distinctMetrics = new ArrayList<>(new LinkedHashSet<>(metrics));
        if (distinctMetrics.size() > MAX_SERIES_METRICS) {
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
        for (SensorMetric metric : distinctMetrics) {
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

    /** CSV 바이트(UTF-8 BOM 포함)와 다운로드 파일명 — {@link #exportCsv} 반환값. */
    public record CsvExport(byte[] content, String filename) {
    }

    /**
     * CSV 내보내기(이슈 #126) — {@code /readings/series}와 <b>동일한 파라미터·동일한 다운샘플
     * 집계</b>를 그대로 CSV로 직렬화한다(원본 이력을 새로 쿼리하지 않는다). 판단 근거:
     * <ol>
     *   <li><b>다운샘플 vs 원본</b>: 이슈 요구사항 자체가 "현재 화면의 필터 상태를 그대로 따르는
     *       것"이다 — 화면 차트가 보여주는 시계열을 파일로 받는 기능이지, 별도의 원본 덤프 기능이
     *       아니다. {@link #series}를 그대로 호출해 재사용하면 두 API가 "같은 필터 → 같은 값"을
     *       영원히 보장한다(로직이 두 곳으로 갈라져 나중에 어긋날 위험이 없다).</li>
     *   <li><b>행 수 상한</b>: series 집계는 이미 <b>구조적으로 크기가 상한선</b>이다(scope=farm도
     *       층 간 평균 1계열로 축약 — contract §4.11 "응답 크기 상한"). 원본을 새로 쿼리했다면
     *       §4.11이 경고한 "농장당 1틱 최대 300행 × 90일 보존"(수백만 행) 규모를 커넥션 하나가
     *       그대로 반환해야 해서 별도의 무거운 상한·페이지네이션 설계가 필요해진다. series 재사용은
     *       그 위험 자체를 없앤다 — 그래도 {@link #MAX_EXPORT_ROWS} 명시 검사는 방어선으로 남긴다
     *       (그 상수 주석 참고).</li>
     * </ol>
     * <b>권한</b>도 series와 동일하게 <b>requireMember(VIEWER 포함)</b>로 둔다(호출하는
     * {@link #series}가 이미 그 검사를 한다) — 내려주는 데이터가 화면에서 VIEWER가 이미 보는 것과
     * 완전히 같은 쿼리·같은 스코프 검증 결과라, 파일 형식이라는 이유만으로 OPERATOR 이상으로
     * 올리면 §2 "VIEWER=조회전용" 정의와 어긋나고 실질적 보안 이득도 없다(VIEWER는 화면 값을 그대로
     * 옮겨 적을 수 있다). "대량 반출"의 위험(무제한 원본 덤프)은 위 설계로 애초에 제거했다 — 그
     * 위험이 실제로 남아 있었다면 OPERATOR 상향이 맞는 판단이었을 것이다.
     */
    public CsvExport exportCsv(Long farmId, Long userId, List<SensorMetric> metrics, String rangeParam,
                                String scopeParam) {
        ReadingSeriesResponse result = series(farmId, userId, metrics, rangeParam, scopeParam);

        int totalRows = result.series().stream().mapToInt(s -> s.points().size()).sum();
        if (totalRows > MAX_EXPORT_ROWS) {
            throw new CustomException(ErrorCode.SA003,
                    "내보내기 행 수(" + totalRows + ")가 상한(" + MAX_EXPORT_ROWS + ")을 초과했습니다. "
                            + "기간·지표·스코프를 좁혀 다시 시도해주세요.");
        }

        byte[] content = toCsvBytes(result);
        String filename = buildExportFilename(farmId, result.range(), result.scope());
        return new CsvExport(content, filename);
    }

    private byte[] toCsvBytes(ReadingSeriesResponse result) {
        StringBuilder csv = new StringBuilder();
        csv.append("measuredAt,metric,unit,value\r\n");
        for (ReadingSeriesResponse.Series s : result.series()) {
            for (ReadingSeriesResponse.Point point : s.points()) {
                csv.append(point.at() != null ? point.at().format(CSV_TIMESTAMP_FORMAT) : "").append(',')
                        .append(s.metric()).append(',')
                        .append(escapeCsvField(s.unit())).append(',')
                        .append(point.value() != null ? point.value() : "")
                        .append("\r\n");
            }
        }

        // ⚠️ UTF-8 BOM 포함(핸드오프 판단 3) — unit 컬럼에 °C·µmol/m²/s처럼 비ASCII 기호가 그대로
        // 실리는데, BOM이 없으면 Excel(Windows 기본 로케일)이 시스템 코드페이지로 잘못 해석해 그
        // 기호가 깨진다. RFC4180을 엄격히 따르는 일부 파서가 BOM을 첫 컬럼명의 일부로 오인하는
        // 부작용이 있지만, 이 파일의 1차 소비자는 "다운로드 후 엑셀로 더블클릭 실행"이라 Excel
        // 호환을 우선한다.
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, withBom, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, withBom, UTF8_BOM.length, body.length);
        return withBom;
    }

    private String escapeCsvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * 다운로드 파일명(핸드오프 요건 — 사용자 입력을 그대로 넣지 않는다) — farmId·range·scope는
     * 전부 이미 서버가 검증을 마친 값이다. {@code scope} 문자열은 {@link ReadingScope#parse}가
     * {@code "farm"} 또는 {@code "{zone|rack|level}:{숫자}"} 형식만 통과시킨 뒤 그대로 echo된 것이라
     * 헤더 인젝션·경로 문자가 섞일 수 없다 — 농장 이름 등 실제 사용자 입력 문자열은 파일명에 쓰지
     * 않는다.
     */
    private String buildExportFilename(Long farmId, String range, String scope) {
        String safeScope = scope.replace(':', '-');
        String timestamp = LocalDateTime.now().format(EXPORT_FILENAME_TIMESTAMP_FORMAT);
        return "sensor-readings_farm" + farmId + "_" + range + "_" + safeScope + "_" + timestamp + ".csv";
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

        LocalDateTime staleThreshold = LocalDateTime.now().minus(tickInterval.multipliedBy(FRESHNESS_TICK_MULTIPLIER));
        List<ReadingMatrixResponse.RackRow> rackRows = racks.stream()
                .map(rack -> toRackRow(rack, levelsByRackId.getOrDefault(rack.getId(), List.of()),
                        latestByLevelId, metric, staleThreshold))
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
                                                     SensorMetric metric, LocalDateTime staleThreshold) {
        List<ReadingMatrixResponse.LevelCell> cells = levels.stream()
                .map(level -> {
                    ReadingLevelLatestProjection latest = latestByLevelId.get(level.getId());
                    LocalDateTime measuredAt = latest != null ? latest.getMeasuredAt() : null;
                    // 신선도 상한(사이클 2 리뷰 P2-2) — 값이 있어도 tick 주기 × 5보다 오래됐으면
                    // 현재값으로 렌더하지 않는다(장비 철거 후에도 readings는 보존되므로§4.10, 상한이
                    // 없으면 두 달 전 값이 "지금 정상"으로 표시된다). measuredAt은 그대로 실어 프론트가
                    // "언제 마지막으로 측정됐는지"는 판단할 수 있게 한다.
                    boolean stale = measuredAt == null || measuredAt.isBefore(staleThreshold);
                    Double value = (latest != null && !stale) ? latest.getValue() : null;
                    String state = value != null
                            ? SensorThresholds.stateOf(metric, value).name()
                            : SensorThresholds.State.IDLE.name();
                    return new ReadingMatrixResponse.LevelCell(level.getLevelNo(), value, measuredAt, state);
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
