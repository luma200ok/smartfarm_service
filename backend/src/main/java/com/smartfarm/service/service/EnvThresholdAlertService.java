package com.smartfarm.service.service;

import com.smartfarm.service.dto.AiEnvironmentResponse;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.entity.FarmEnvThreshold;
import com.smartfarm.service.repository.FarmEnvThresholdRepository;
import com.smartfarm.service.repository.FarmRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 환경 임계치 평가·알림(contract §4.6, 이슈 #52) — {@code EnvironmentSnapshotPoller}가 새 스냅샷을
 * 적재한 직후(중복 skip이 아닐 때만) 호출한다. 연속 2틱 이탈 시 발동, 농장×항목×방향별 쿨다운
 * 30분. 상태는 메모리(단일 인스턴스 전제, 재시작 시 초기화 수용 — contract 명시).
 *
 * <p>단일 스케줄러 스레드가 순차 호출하므로 별도 락은 필요 없다({@link ConcurrentHashMap}은
 * 안전망일 뿐 실질적 동시 접근은 없다).
 */
@Slf4j
@Component
public class EnvThresholdAlertService {

    static final int CONSECUTIVE_THRESHOLD = 2;
    static final Duration COOLDOWN = Duration.ofMinutes(30);

    private final FarmEnvThresholdRepository farmEnvThresholdRepository;
    private final FarmRepository farmRepository;
    private final EnvThresholdWebhookNotifier notifier;
    private final AlarmEventService alarmEventService;
    private final Clock clock;

    private final ConcurrentHashMap<AlertKey, AlertState> states = new ConcurrentHashMap<>();

    @Autowired
    public EnvThresholdAlertService(FarmEnvThresholdRepository farmEnvThresholdRepository,
                                     FarmRepository farmRepository,
                                     EnvThresholdWebhookNotifier notifier,
                                     AlarmEventService alarmEventService) {
        this(farmEnvThresholdRepository, farmRepository, notifier, alarmEventService, Clock.systemDefaultZone());
    }

    /** 테스트 전용 — 30분 쿨다운 만료를 실시간 대기 없이 검증하기 위해 Clock을 주입한다(package-private). */
    EnvThresholdAlertService(FarmEnvThresholdRepository farmEnvThresholdRepository,
                              FarmRepository farmRepository,
                              EnvThresholdWebhookNotifier notifier,
                              AlarmEventService alarmEventService,
                              Clock clock) {
        this.farmEnvThresholdRepository = farmEnvThresholdRepository;
        this.farmRepository = farmRepository;
        this.notifier = notifier;
        this.alarmEventService = alarmEventService;
        this.clock = clock;
    }

    public void evaluate(AiEnvironmentResponse.Indoor indoor) {
        if (indoor == null) {
            return;
        }
        List<FarmEnvThreshold> thresholds = farmEnvThresholdRepository.findEnabledWithWebhookConfigured();
        for (FarmEnvThreshold threshold : thresholds) {
            evaluateMetric(threshold, EnvMetric.INDOOR_TEMP, indoor.temp(),
                    threshold.getIndoorTempMin(), threshold.getIndoorTempMax());
            evaluateMetric(threshold, EnvMetric.INDOOR_HUMIDITY, indoor.humidity(),
                    threshold.getIndoorHumidityMin(), threshold.getIndoorHumidityMax());
        }
    }

    private void evaluateMetric(FarmEnvThreshold threshold, EnvMetric metric, Double value, Double min, Double max) {
        if (value == null) {
            return;
        }
        if (min != null) {
            evaluateDirection(threshold, metric, EnvDirection.LOW, value < min, value, min);
        }
        if (max != null) {
            evaluateDirection(threshold, metric, EnvDirection.HIGH, value > max, value, max);
        }
    }

    private void evaluateDirection(FarmEnvThreshold threshold, EnvMetric metric, EnvDirection direction,
                                    boolean breached, double value, double boundary) {
        AlertKey key = new AlertKey(threshold.getFarmId(), metric, direction);
        AlertState state = states.computeIfAbsent(key, k -> new AlertState());

        if (!breached) {
            // getAndSet(0) == 0이면 이미 정상이던 틱이라 열린 알람이 있을 수 없어 조회를 생략한다
            // (이슈 #116 — 매 정상 틱마다 DB 조회하는 낭비 방지, previousConsecutive>0일 때만
            // "브리치 → 정상 복귀" 전이가 실제로 발생했다는 뜻).
            int previousConsecutive = state.consecutiveBreaches.getAndSet(0);
            if (previousConsecutive > 0) {
                alarmEventService.autoResolveIfOpen(threshold.getFarmId(), metricKeyOf(metric, direction));
            }
            return;
        }
        int consecutive = state.consecutiveBreaches.incrementAndGet();
        if (consecutive < CONSECUTIVE_THRESHOLD) {
            return;
        }

        // 알람 이벤트 생성은 웹훅 쿨다운과 무관하게(멱등이라 안전) 연속 임계치 확정 시점에 시도한다
        // — 웹훅 쿨다운은 알림 스팸 방지용 rate limit이지 알람 존재 자체를 막을 이유는 아니다.
        recordAlarmBreach(threshold, metric, direction, value, boundary);

        Instant now = Instant.now(clock);
        Instant lastNotifiedAt = state.lastNotifiedAt.get();
        if (lastNotifiedAt != null && Duration.between(lastNotifiedAt, now).compareTo(COOLDOWN) < 0) {
            return; // 쿨다운 중
        }
        state.lastNotifiedAt.set(now);

        farmRepository.findById(threshold.getFarmId())
                .ifPresent(farm -> notifier.notifyBreach(farm, metric, direction, value, boundary));
    }

    /**
     * 알람 이벤트 생성 훅(이슈 #116) — {@link AlarmEventService#recordBreach}가 1차로 멱등을
     * 보장하지만(같은 farm×metricKey 조합 미해결 이벤트 존재 시 no-op), 그 조회-후-저장 자체가
     * 원자적이지 않아 동시 브리치 레이스에서는 DB의 partial unique index(V19, 2차 방어선)가
     * {@link DataIntegrityViolationException}을 던질 수 있다. {@code recordBreach}의 트랜잭션이
     * 이미 rollback-only로 마킹된 지점(그 내부)이 아니라 여기서 잡아야 한다 — 그렇지 않으면
     * evaluateDirection → evaluate()의 for 루프를 타고 올라가 {@code EnvironmentSnapshotPoller}의
     * catch(Exception)까지 전파되며, 그 사이 아직 평가되지 않은 "뒤 순서 농장 전부"가 이번 틱에서
     * 알람·웹훅 모두 유실된다(SensorSimulatorService의 농장 단위 격리 catch와 동일한 이유).
     * severity는 현재 CRITICAL/WARNING을 구분하는 별도 기준이 없어 전부 WARNING으로 기록한다
     * (후속 이슈 — 항목별/이탈폭별 등급 분화).
     */
    private void recordAlarmBreach(FarmEnvThreshold threshold, EnvMetric metric, EnvDirection direction,
                                    double value, double boundary) {
        String message = metric.label() + " " + direction.label()
                + " (현재 " + value + metric.unit() + " / 기준 " + boundary + metric.unit() + ")";
        Long farmId = threshold.getFarmId();
        String metricKey = metricKeyOf(metric, direction);
        try {
            alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                    metricKey, message, LocalDateTime.now(clock), threshold.getId());
        } catch (DataIntegrityViolationException e) {
            log.debug("알람 이벤트 중복 생성(멱등 2차 방어선) — 무시: farm={}, metric={}", farmId, metricKey);
        }
    }

    /** farm×항목×방향 조합을 표현하는 alarm_events.metric_key 값(예: INDOOR_TEMP_HIGH, 이슈 #116). */
    private static String metricKeyOf(EnvMetric metric, EnvDirection direction) {
        return metric.name() + "_" + direction.name();
    }

    /** 테스트 격리용 — 싱글턴 빈이라 통합 테스트 간 상태가 새지 않도록 초기화한다. */
    public void resetState() {
        states.clear();
    }

    /**
     * 특정 농장의 연속 이탈 상태를 리셋한다(리뷰 P3, 이슈 #52) — {@code EnvThresholdService}가
     * 임계치 설정을 변경/비활성화한 직후 호출한다. 설정 변경 전 누적된 연속 카운트를 그대로
     * 이어받으면 새 기준값으로는 아직 1틱째인 이탈이 곧바로 2틱 발동으로 오판정될 수 있어(예:
     * 기준을 널널하게 바꿨다가 다시 좁힐 때), 설정이 바뀐 시점부터 "새로 2틱"부터 세도록 한다.
     */
    public void resetFarm(Long farmId) {
        states.keySet().removeIf(key -> key.farmId().equals(farmId));
    }

    private record AlertKey(Long farmId, EnvMetric metric, EnvDirection direction) {
    }

    private static final class AlertState {
        private final AtomicInteger consecutiveBreaches = new AtomicInteger(0);
        private final AtomicReference<Instant> lastNotifiedAt = new AtomicReference<>();
    }
}
