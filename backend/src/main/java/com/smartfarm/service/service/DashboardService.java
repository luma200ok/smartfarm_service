package com.smartfarm.service.service;

import com.smartfarm.service.dto.DashboardFarmsResponse;
import com.smartfarm.service.dto.FarmDashboardResponse;
import com.smartfarm.service.dto.FarmDashboardStatus;
import com.smartfarm.service.dto.FarmSummaryResponse;
import com.smartfarm.service.entity.AlarmEventStatus;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.repository.AlarmEventRepository;
import com.smartfarm.service.repository.AlarmSeverityFarmCountProjection;
import com.smartfarm.service.repository.FarmLatestAlarmProjection;
import com.smartfarm.service.repository.FarmMemberRepository;
import com.smartfarm.service.repository.FarmRackAggregateProjection;
import com.smartfarm.service.repository.RackRepository;
import com.smartfarm.service.repository.ReadingFarmDailyTrendProjection;
import com.smartfarm.service.repository.ReadingFarmLatestProjection;
import com.smartfarm.service.repository.SensorReadingRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 대시보드 농장 카드 집계(이슈 #139, 시안 `01-dashboard-home` 4열 카드 선행). {@code FarmService}
 * (findMyFarms)·{@code ZoneService}(findZoneTree)·{@code ReadingService}(latest/series)·
 * {@code AlarmEventService}(unacknowledgedCount)는 전부 <b>단일 농장용</b>이고 각자
 * {@code FarmAccessGuard}를 호출한다 — 이 카드 응답처럼 "내 농장 전체"를 한 화면에 그리는 용도로
 * 농장마다 그 메서드들을 반복 호출하면 그 자체가 N+1이라, 이 이슈가 막으려는 문제를 재생산한다.
 *
 * <p>그래서 이 서비스는 그 서비스들을 재사용하지 않고 <b>repository를 직접 배치(batch) 조회</b>한다
 * — farm id 목록을 한 번에 모아 IN 절로 묶어, 농장 수와 무관하게 쿼리 개수를 상수로 고정한다(쿼리
 * 5개: 내 농장 목록·랙/층 집계·미확인 알람 severity 집계·최근 알람 메시지·지표 3열 최신값·7일 추이 —
 * 정확히는 6개, 전부 farm 수 증가와 무관). {@code findMyFarms}와 동일하게 <b>farm-scoped 가드가
 * 아니라 사용자 기준 필터</b>가 보안 경계다 — {@code FarmMemberRepository#findMyFarms}의
 * {@code WHERE fm.userId = :userId}가 타 사용자 농장 혼입을 구조적으로 막는다(이슈 #122 선례).
 *
 * <p><b>PENDING 멤버십 농장은 제외한다</b>(판단, 이슈 #139 handoff) — {@code GET /api/farms}는
 * PENDING도 포함해 FE가 "대기 중" 뱃지를 그리지만, 이 카드가 실어주는 랙/층 수·지표·알람은 전부
 * {@code FarmAccessGuard#requireMember}가 지키는 farm-scoped 내부 데이터와 동일한 성격이다.
 * PENDING은 그 표면에 접근할 수 없다(F008) — 카드 하나가 그 원칙을 우회해 내부 지표를 노출하면 안
 * 된다. {@code FarmRole#isActive()}(ADMIN/OPERATOR/VIEWER)로 필터한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    /**
     * 카드 지표 3열(시안 고정) — 온도·습도는 재배환경 기본 지표, EC는 양액 관리 핵심 지표라 세
     * 지표의 대표성이 가장 높다(시안 `01-dashboard-home` 실측 — 카드마다 항상 이 3종만 표시).
     */
    static final List<SensorMetric> CARD_METRICS = List.of(
            SensorMetric.TEMPERATURE, SensorMetric.HUMIDITY, SensorMetric.EC);

    /**
     * trend7d 대표 지표 — CARD_METRICS 중 온도로 고정한다. 농장마다 다른 "문제 지표"를 동적으로
     * 고르면(EC 위반 농장은 EC 추이, 습도 위반 농장은 습도 추이 등) 카드 그리드를 훑어볼 때 막대가
     * 매번 다른 기준을 나타내게 되어 "한눈에 비교"라는 이슈 목적과 어긋난다. 온도는 전 재배 유형에
     * 공통으로 존재하는 환경 지표라 대표값으로 가장 안전하다.
     */
    static final SensorMetric TREND_METRIC = SensorMetric.TEMPERATURE;

    /** trend7d 조회 기간(달력일 7일, 오늘 포함). */
    private static final int TREND_DAYS = 7;

    private final FarmMemberRepository farmMemberRepository;
    private final RackRepository rackRepository;
    private final AlarmEventRepository alarmEventRepository;
    private final SensorReadingRepository sensorReadingRepository;

    /**
     * 이 대시보드 집계 대상 농장 수 상한(이슈 #139 handoff 판단) — 농장 생성 자체에는 아직 상한이
     * 없다(#70). 이 API는 배치 쿼리 덕분에 쿼리 "개수"는 농장 수와 무관하지만, IN 절 크기·응답
     * 페이로드·트렌드 집계 대상 행 수는 농장 수에 비례한다. 카드 그리드 UX 자체가 수십 개를 넘으면
     * "한눈에 비교"가 성립하지 않으므로, 초과분은 <b>에러 없이 조용히 자르고 WARN 로그만 남긴다</b>
     * (sensor-simulator의 max-rows-per-farm 초과 처리와 동일 원칙 — 조회 API이므로 사용자 요청을
     * 거부하기보다 상한 내로 degrade한다). 농장 생성 상한(#70)이 도입되면 이 값도 함께 재검토한다.
     * {@code sensor-simulator.max-rows-per-farm} 등 다른 상한 값과 동일하게 @Value로 외부화해
     * 테스트가 낮은 상한으로 오버라이드해 절단 동작 자체를 검증할 수 있게 한다.
     */
    @Value("${dashboard.max-farms:50}")
    private int maxDashboardFarms;

    /** {@code ReadingService}와 동일한 신선도 상한 기준(tick 주기 × 5) — 카드 지표도 "현재값"을
     * 참칭하지 않도록 오래된 값은 null로 떨어뜨린다. */
    @Value("${sensor-simulator.tick-fixed-delay:PT60S}")
    private Duration tickInterval;

    private static final int FRESHNESS_TICK_MULTIPLIER = 5;

    public DashboardFarmsResponse findMyFarmsDashboard(Long userId) {
        List<FarmSummaryResponse> myFarms = farmMemberRepository.findMyFarms(userId).stream()
                .filter(farm -> farm.myRole().isActive())
                .toList();
        if (myFarms.isEmpty()) {
            return new DashboardFarmsResponse(List.of(), 0, false);
        }
        // 절단 전 개수를 먼저 고정한다 — totalCount는 항상 이 값이어야 한다(이슈 #140, 응답이
        // 평문 배열이던 시절엔 FE가 절단 여부를 알 방법이 없어 카드가 그냥 사라진 것처럼 보였다).
        int totalCount = myFarms.size();
        boolean truncated = totalCount > maxDashboardFarms;
        if (truncated) {
            log.warn("홈 대시보드 집계 대상 농장이 상한을 초과해 잘랐습니다: userId={}, count={}, cap={}",
                    userId, totalCount, maxDashboardFarms);
            myFarms = myFarms.subList(0, maxDashboardFarms);
        }

        List<Long> farmIds = myFarms.stream().map(FarmSummaryResponse::id).toList();

        Map<Long, FarmRackAggregateProjection> rackAggByFarmId = rackRepository.aggregateByFarmIds(farmIds).stream()
                .collect(Collectors.toMap(FarmRackAggregateProjection::getFarmId, p -> p));

        Map<Long, List<AlarmSeverityFarmCountProjection>> severityCountsByFarmId =
                alarmEventRepository.countBySeverityForFarmIds(farmIds, AlarmEventStatus.UNACKNOWLEDGED).stream()
                        .collect(Collectors.groupingBy(AlarmSeverityFarmCountProjection::getFarmId));

        Map<Long, String> latestMessageByFarmId = alarmEventRepository.findLatestMessageByFarmIds(farmIds).stream()
                .collect(Collectors.toMap(FarmLatestAlarmProjection::getFarmId, FarmLatestAlarmProjection::getMessage));

        List<String> cardMetricNames = CARD_METRICS.stream().map(SensorMetric::name).toList();
        Map<Long, Map<String, ReadingFarmLatestProjection>> latestMetricsByFarmId =
                sensorReadingRepository.findLatestByFarmIdsAndMetrics(farmIds, cardMetricNames).stream()
                        .collect(Collectors.groupingBy(ReadingFarmLatestProjection::getFarmId,
                                Collectors.toMap(ReadingFarmLatestProjection::getMetric, p -> p)));

        LocalDate today = LocalDate.now();
        LocalDateTime trendSince = today.minusDays(TREND_DAYS - 1L).atStartOfDay();
        Map<Long, Map<LocalDate, Double>> trendByFarmId = sensorReadingRepository
                .findDailyTrendByFarmIds(farmIds, TREND_METRIC.name(), trendSince).stream()
                .collect(Collectors.groupingBy(ReadingFarmDailyTrendProjection::getFarmId,
                        Collectors.toMap(ReadingFarmDailyTrendProjection::getBucketDate,
                                ReadingFarmDailyTrendProjection::getValue)));

        LocalDateTime staleThreshold = LocalDateTime.now().minus(tickInterval.multipliedBy(FRESHNESS_TICK_MULTIPLIER));

        List<FarmDashboardResponse> farms = myFarms.stream()
                .map(farm -> toDashboardResponse(farm,
                        rackAggByFarmId.get(farm.id()),
                        severityCountsByFarmId.getOrDefault(farm.id(), List.of()),
                        latestMessageByFarmId.get(farm.id()),
                        latestMetricsByFarmId.getOrDefault(farm.id(), Map.of()),
                        trendByFarmId.getOrDefault(farm.id(), Map.of()),
                        staleThreshold, today))
                .toList();
        return new DashboardFarmsResponse(farms, totalCount, truncated);
    }

    private FarmDashboardResponse toDashboardResponse(FarmSummaryResponse farm,
                                                        FarmRackAggregateProjection rackAgg,
                                                        List<AlarmSeverityFarmCountProjection> severityCounts,
                                                        String latestAlarmMessage,
                                                        Map<String, ReadingFarmLatestProjection> latestMetrics,
                                                        Map<LocalDate, Double> trendByDate,
                                                        LocalDateTime staleThreshold, LocalDate today) {
        int rackCount = rackAgg != null ? rackAgg.getRackCount().intValue() : 0;
        int levelCount = rackAgg != null ? rackAgg.getLevelCount().intValue() : 0;

        long unacknowledgedCount = severityCounts.stream()
                .mapToLong(AlarmSeverityFarmCountProjection::getEventCount)
                .sum();
        FarmDashboardStatus status = deriveStatus(severityCounts);

        List<FarmDashboardResponse.MetricValue> metrics = CARD_METRICS.stream()
                .map(metric -> toMetricValue(metric, latestMetrics.get(metric.name()), staleThreshold))
                .toList();

        List<FarmDashboardResponse.TrendPoint> trend7d = buildTrend(trendByDate, today);

        return new FarmDashboardResponse(farm.id(), farm.name(), farm.cropType(), rackCount, levelCount,
                status, unacknowledgedCount, metrics, trend7d, latestAlarmMessage);
    }

    private FarmDashboardStatus deriveStatus(List<AlarmSeverityFarmCountProjection> severityCounts) {
        boolean hasCritical = severityCounts.stream()
                .anyMatch(p -> p.getSeverity() == AlarmSeverity.CRITICAL);
        if (hasCritical) {
            return FarmDashboardStatus.CRITICAL;
        }
        boolean hasWarning = severityCounts.stream()
                .anyMatch(p -> p.getSeverity() == AlarmSeverity.WARNING);
        return hasWarning ? FarmDashboardStatus.WARNING : FarmDashboardStatus.NORMAL;
    }

    private FarmDashboardResponse.MetricValue toMetricValue(SensorMetric metric,
                                                              ReadingFarmLatestProjection latest,
                                                              LocalDateTime staleThreshold) {
        boolean fresh = latest != null && latest.getMeasuredAt() != null
                && !latest.getMeasuredAt().isBefore(staleThreshold);
        Double value = fresh ? latest.getValue() : null;
        boolean outOfRange = value != null && SensorThresholds.stateOf(metric, value) != SensorThresholds.State.OK;
        return new FarmDashboardResponse.MetricValue(metric.name(), metric.unit(), value, outOfRange);
    }

    private List<FarmDashboardResponse.TrendPoint> buildTrend(Map<LocalDate, Double> trendByDate, LocalDate today) {
        List<FarmDashboardResponse.TrendPoint> points = new ArrayList<>(TREND_DAYS);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Double value = trendByDate.get(date);
            String state = value != null
                    ? SensorThresholds.stateOf(TREND_METRIC, value).name()
                    : SensorThresholds.State.IDLE.name();
            points.add(new FarmDashboardResponse.TrendPoint(date, value, state));
        }
        return points;
    }
}
