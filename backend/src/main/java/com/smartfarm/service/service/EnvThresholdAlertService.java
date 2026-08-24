package com.smartfarm.service.service;

import com.smartfarm.service.dto.AiEnvironmentResponse;
import com.smartfarm.service.entity.AlarmRule;
import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.Device;
import com.smartfarm.service.entity.DeviceStatus;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.repository.AlarmRuleRepository;
import com.smartfarm.service.repository.DeviceRepository;
import com.smartfarm.service.repository.FarmRepository;
import com.smartfarm.service.repository.ReadingScopeLatestProjection;
import com.smartfarm.service.repository.SensorReadingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 알람 규칙 평가·알림(contract §4.6·§4.13, 이슈 #52 → #116 → #118) — {@code EnvironmentSnapshotPoller}가
 * 새 스냅샷을 적재한 직후(중복 skip이 아닐 때만) 호출한다.
 *
 * <p><b>#118 변경 요약</b>: 평가 대상이 {@code farm_env_thresholds}(농장당 1행 × 온·습도 4컬럼)에서
 * {@code alarm_rules}(농장당 N개 규칙)로 바뀌었다. 그에 따라
 * <ul>
 *   <li>발동 조건이 "연속 2틱" 하드코딩에서 규칙별 {@code durationSeconds}(최초 이탈 시각부터의
 *       경과 시간) 판정으로 일반화됐다.</li>
 *   <li>지표 데이터 소스가 3종으로 늘었다 — env_snapshots / sensor_readings / 장비 통신 두절.</li>
 *   <li>평가 스코프가 농장/존/랙/층으로 나뉜다(같은 지표라도 랙이 다르면 독립 알람).</li>
 *   <li>알람 등급을 규칙이 결정한다(#116의 WARNING 고정 해소).</li>
 *   <li>쿨다운·인메모리 상태의 단위가 "농장×항목×방향"에서 <b>규칙</b>으로 바뀌었다.</li>
 * </ul>
 *
 * <p>상태는 메모리(단일 인스턴스 전제, 재시작 시 초기화 수용 — contract 명시). 단일 스케줄러
 * 스레드가 순차 호출하므로 별도 락은 필요 없다({@link ConcurrentHashMap}은 안전망일 뿐 실질적
 * 동시 접근은 없다).
 *
 * <p>⚠️ <b>PR #117에서 확보한 불변식 4종은 이 클래스의 핵심이며 절대 깨지 않는다</b> — 각각
 * {@link #evaluate}, {@link AlarmRuleRepository#findEnabled()}, {@link AlarmRule#metricKey()},
 * {@link #evaluateRule}의 주석에 근거와 함께 남겨 뒀다.
 */
@Slf4j
@Component
public class EnvThresholdAlertService {

    /** 웹훅 재발송 억제 간격(규칙 단위) — contract §4.6의 30분 쿨다운. */
    static final Duration COOLDOWN = Duration.ofMinutes(30);

    /**
     * 측정값 신선도 상한 — 이보다 오래된 값밖에 없으면 "관측 부재"로 보고 평가를 건너뛴다.
     * {@code ReadingService}의 신선도 규칙(tick 주기 60s × 5)과 같은 값이다: 정상 운영에서 5틱을
     * 연달아 놓쳤다면 그 스코프는 더 이상 관측되고 있지 않다고 본다. 낡은 값으로 알람을 발동시키지도
     * (유령 알람), 해소하지도(놓친 알람) 않기 위한 공통 가드다.
     */
    static final Duration MEASUREMENT_FRESHNESS = Duration.ofMinutes(5);

    /** {@code alarm_events.message}(VARCHAR(500)) 한도 — 규칙 이름이 사용자 입력이라 잘라 담는다. */
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final AlarmRuleRepository alarmRuleRepository;
    private final FarmRepository farmRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final DeviceRepository deviceRepository;
    private final EnvThresholdWebhookNotifier notifier;
    private final AlarmEventService alarmEventService;
    private final Clock clock;

    private final ConcurrentHashMap<AlertKey, AlertState> states = new ConcurrentHashMap<>();

    @Autowired
    public EnvThresholdAlertService(AlarmRuleRepository alarmRuleRepository,
                                     FarmRepository farmRepository,
                                     SensorReadingRepository sensorReadingRepository,
                                     DeviceRepository deviceRepository,
                                     EnvThresholdWebhookNotifier notifier,
                                     AlarmEventService alarmEventService) {
        this(alarmRuleRepository, farmRepository, sensorReadingRepository, deviceRepository, notifier,
                alarmEventService, Clock.systemDefaultZone());
    }

    /**
     * 테스트 전용 — 지속시간·30분 쿨다운 만료를 실시간 대기 없이 검증하기 위해 Clock을 주입한다
     * (package-private).
     */
    EnvThresholdAlertService(AlarmRuleRepository alarmRuleRepository,
                              FarmRepository farmRepository,
                              SensorReadingRepository sensorReadingRepository,
                              DeviceRepository deviceRepository,
                              EnvThresholdWebhookNotifier notifier,
                              AlarmEventService alarmEventService,
                              Clock clock) {
        this.alarmRuleRepository = alarmRuleRepository;
        this.farmRepository = farmRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.deviceRepository = deviceRepository;
        this.notifier = notifier;
        this.alarmEventService = alarmEventService;
        this.clock = clock;
    }

    /**
     * 활성 규칙 전체를 1회 평가한다.
     *
     * <p>{@code indoor}는 이번 틱의 env_snapshots 관측이며 null일 수 있다(ai-server 부분 응답).
     * null이어도 <b>조기 return 하지 않는다</b> — ENV_SNAPSHOT 규칙만 관측 부재로 건너뛰고,
     * sensor_readings·장비 통신 두절 규칙은 자체 데이터 소스가 있으므로 계속 평가돼야 한다.
     * (#117까지는 indoor가 유일한 데이터 소스라 여기서 즉시 return했다.)
     */
    public void evaluate(AiEnvironmentResponse.Indoor indoor) {
        // 알람 이벤트(영속 기록)와 웹훅(알림 채널)은 다른 관심사다(이슈 #116 리뷰 P2-B) — 평가
        // 대상은 enabled=true인 규칙 전체이며 웹훅 URL 설정 여부와 무관하다. 웹훅 발송 스킵은
        // EnvThresholdWebhookNotifier#notifyBreach가 개별 farm의 webhookUrl==null을 보고 이미
        // 내부에서 처리한다. soft delete된 농장 제외는 findEnabled()의 Farm 서브쿼리 책임이다
        // (이슈 #116 리뷰 회귀-B — 그 쿼리 javadoc 참고).
        List<AlarmRule> rules = alarmRuleRepository.findEnabled();
        for (AlarmRule rule : rules) {
            // ⚠️ 규칙 단위 예외 격리(이슈 #116 리뷰 회귀-A) — 이 catch를 제거하면 규칙 하나의 실패가
            // for 루프를 끊고 EnvironmentSnapshotPoller.poll의 catch(Exception)까지 전파돼, 아직
            // 평가되지 않은 "뒤 순서 규칙 전부"가 이번 틱에서 알람·웹훅 모두 유실된다. 실패 경로는
            // 하나가 아니다: alarmEventService.recordBreach의 DataIntegrityViolationException(멱등
            // 2차 방어선), autoResolveIfOpen의 ObjectOptimisticLockingFailureException(사용자가 같은
            // 이벤트를 동시에 acknowledge/resolve), 그리고 #118에서 늘어난 데이터 소스 조회
            // (sensor_readings·devices) 자체의 오류까지. 개별 호출부만 감싸는 걸로는 루프 바디의
            // 나머지를 못 덮으므로 루프 바디 전체를 규칙 단위로 격리한다.
            try {
                evaluateRule(rule, indoor);
            } catch (Exception e) {
                log.warn("알람 규칙 평가 실패 — 이 규칙만 건너뜀: farmId={}, ruleId={}, cause={}",
                        rule.getFarmId(), rule.getId(), e.getClass().getSimpleName());
            }
        }
    }

    private void evaluateRule(AlarmRule rule, AiEnvironmentResponse.Indoor indoor) {
        Observation observation = observe(rule, indoor);
        if (observation == null) {
            // 관측 부재(해당 지표의 신선한 값이 없거나, 스코프에 장비가 없음) — 판정 불가다.
            // 정상 복귀가 아니므로 자동 해소도 하지 않는다(관측이 끊긴 것과 정상으로 돌아온 것은
            // 다른 사건이다). 인메모리 지속시간 상태도 건드리지 않아, 관측이 재개되면 이어서 센다.
            return;
        }

        AlertKey key = new AlertKey(rule.getFarmId(), rule.getId());
        AlertState state = states.computeIfAbsent(key, k -> new AlertState());
        Instant now = Instant.now(clock);

        if (!observation.breached()) {
            // ⚠️ 인메모리 상태만으로 "열린 알람이 없다"고 단정할 수 없다(이슈 #116 리뷰 P2-A) —
            // states는 앱 재시작이나 resetFarm 호출(EnvThresholdService.updateThresholds·
            // AlarmRuleService의 규칙 변경 때마다 발생하므로 재시작보다 훨씬 흔하다)로 비워질 수
            // 있는데, 그 시점에도 DB엔 여전히 열린 이벤트가 남아 있을 수 있다. 따라서 인메모리
            // 상태와 무관하게 정상 틱마다 DB 상태를 직접 확인해 자동 해소를 시도한다 —
            // (farm_id, status) 인덱스를 타고 autoResolveIfOpen 자체도 열린 이벤트가 없으면
            // no-op이라 안전하다. 이 무조건 호출을 조건부로 바꾸면 유령 알람이 영구 고착된다.
            state.firstBreachAt.set(null);
            alarmEventService.autoResolveIfOpen(rule.getFarmId(), rule.metricKey());
            return;
        }

        // 지속시간 판정 — 최초 이탈 시각을 기록해 두고 그로부터 durationSeconds가 지나야 발동한다
        // (#117까지의 "연속 2틱" 하드코딩의 일반화). 벽시계 기준이라 폴링 주기가 바뀌거나 틱을
        // 몇 번 놓쳐도 사용자가 설정한 시간 의미가 유지된다.
        state.firstBreachAt.compareAndSet(null, now);
        Instant firstBreachAt = state.firstBreachAt.get();
        if (Duration.between(firstBreachAt, now).getSeconds() < rule.getDurationSeconds()) {
            return;
        }

        // 알람 이벤트 생성은 웹훅 쿨다운과 무관하게(멱등이라 안전) 지속시간 충족 시점에 시도한다
        // — 웹훅 쿨다운은 알림 스팸 방지용 rate limit이지 알람 존재 자체를 막을 이유는 아니다.
        String message = buildMessage(rule, observation);
        recordAlarmBreach(rule, message);

        Instant lastNotifiedAt = state.lastNotifiedAt.get();
        if (lastNotifiedAt != null && Duration.between(lastNotifiedAt, now).compareTo(COOLDOWN) < 0) {
            return; // 쿨다운 중
        }
        state.lastNotifiedAt.set(now);

        farmRepository.findById(rule.getFarmId())
                .ifPresent(farm -> notifier.notifyBreach(farm, rule.getSeverity(), message));
    }

    // ── 지표 데이터 소스 라우팅(이슈 #118) ────────────────────────────────────────

    /** 관측 결과. {@code null} 반환 = 관측 부재(판정 불가). */
    private record Observation(boolean breached, String detail) {
    }

    private Observation observe(AlarmRule rule, AiEnvironmentResponse.Indoor indoor) {
        return switch (rule.getSource()) {
            case ENV_SNAPSHOT -> observeEnvSnapshot(rule, indoor);
            case SENSOR_READING -> observeSensorReading(rule);
            case DEVICE_HEARTBEAT -> observeDeviceHeartbeat(rule);
        };
    }

    /**
     * V9 {@code env_snapshots} 경로(#117까지의 유일한 경로) — ai-server의 단일 하우스 실측이라
     * farmId가 없고, 그래서 규칙의 스코프는 FARM으로 강제돼 있다(V20 CHECK 제약).
     */
    private Observation observeEnvSnapshot(AlarmRule rule, AiEnvironmentResponse.Indoor indoor) {
        if (indoor == null) {
            return null;
        }
        EnvMetric metric = EnvMetric.valueOf(rule.getMetric());
        Double value = metric == EnvMetric.INDOOR_TEMP ? indoor.temp() : indoor.humidity();
        if (value == null) {
            return null; // 부분 응답 — 이 지표만 관측 부재
        }
        return new Observation(rule.isBreached(value),
                metric.label() + " " + rule.getComparator().label()
                        + " (현재 " + value + metric.unit() + " / 기준 " + rule.boundaryDescription()
                        + metric.unit() + ")");
    }

    /**
     * V15 {@code sensor_readings} 경로 — 스코프 안의 최신 tick에서 device 간 평균을 낸 값 1개를
     * 본다({@code findLatestInScope}가 {@code /readings/latest}와 같은 두 단계 집계를 쓴다).
     */
    private Observation observeSensorReading(AlarmRule rule) {
        SensorMetric metric = SensorMetric.valueOf(rule.getMetric());
        LocalDateTime since = LocalDateTime.now(clock).minus(MEASUREMENT_FRESHNESS);
        List<ReadingScopeLatestProjection> rows = sensorReadingRepository.findLatestInScope(
                rule.getFarmId(), metric.name(), since,
                scopeFilter(rule, AlarmScopeType.ZONE),
                scopeFilter(rule, AlarmScopeType.RACK),
                scopeFilter(rule, AlarmScopeType.LEVEL));
        if (rows.isEmpty() || rows.get(0).getValue() == null) {
            return null; // 신선한 측정값 없음 — 관측 부재
        }
        double value = rows.get(0).getValue();
        return new Observation(rule.isBreached(value),
                metric.name() + " " + rule.getComparator().label()
                        + " (현재 " + value + metric.unit() + " / 기준 " + rule.boundaryDescription()
                        + metric.unit() + ")");
    }

    /**
     * 장비 통신 두절 경로 — 스코프 안에 <b>응답하지 않는 활성 장비가 하나라도</b> 있으면 이탈이다.
     *
     * <p>판정 신호가 둘인 이유: {@link DeviceStatus#OFFLINE}은 계약 §4.10이 정의한 통신 두절
     * 상태로 현재 이 코드베이스에서 실제로 관리되는 유일한 신호이고, {@code lastSeenAt}은 §4.10
     * 모델에 컬럼만 있고 <b>아직 쓰는 곳이 없다</b>. 그래서 {@code lastSeenAt == null}은 부재로
     * 치지 않는다 — 그러지 않으면 전 장비가 상시 발동한다. 값이 채워지기 시작하면(수신 기록 writer
     * 도입 시) 이 판정이 자동으로 살아난다.
     *
     * <p>신선도 기준을 {@code durationSeconds}가 아니라 고정 {@link #MEASUREMENT_FRESHNESS}로 두는
     * 것도 의도적이다 — 지속시간은 {@link #evaluateRule}의 공통 게이트가 담당해야 소스마다 발동
     * 시점의 의미가 같아진다(부재 판정에도 durationSeconds를 쓰면 실제 발동까지 2배가 걸린다).
     *
     * <p>스코프에 활성 장비가 하나도 없으면 관측 부재(null)다 — "무응답 0건"이 아니라 "판정 대상이
     * 없음"이므로, 장비를 다 지운 존에서 열린 알람이 근거 없이 자동 해소되는 것을 막는다.
     */
    private Observation observeDeviceHeartbeat(AlarmRule rule) {
        List<Device> devices = devicesInScope(rule);
        if (devices.isEmpty()) {
            return null;
        }
        LocalDateTime staleThreshold = LocalDateTime.now(clock).minus(MEASUREMENT_FRESHNESS);
        List<Device> silent = devices.stream()
                .filter(device -> device.getStatus() == DeviceStatus.OFFLINE
                        || (device.getLastSeenAt() != null && device.getLastSeenAt().isBefore(staleThreshold)))
                .toList();
        if (silent.isEmpty()) {
            return new Observation(false, "장비 통신 정상 (" + devices.size() + "대)");
        }
        return new Observation(true, "장비 통신 두절 — " + silent.get(0).getName()
                + (silent.size() > 1 ? " 외 " + (silent.size() - 1) + "대" : ""));
    }

    private List<Device> devicesInScope(AlarmRule rule) {
        return switch (rule.getScopeType()) {
            case FARM -> deviceRepository.findByFarmIdOrderByIdAsc(rule.getFarmId());
            case ZONE -> deviceRepository.findByZoneIdOrderByIdAsc(rule.getScopeId());
            case RACK -> deviceRepository.findByRackIdOrderByIdAsc(rule.getScopeId());
            case LEVEL -> deviceRepository.findByRackLevelIdOrderByIdAsc(rule.getScopeId());
        };
    }

    /** 규칙 스코프가 {@code type}일 때만 scopeId를, 아니면 null을 준다(스코프 필터 3종 조립용). */
    private Long scopeFilter(AlarmRule rule, AlarmScopeType type) {
        return rule.getScopeType() == type ? rule.getScopeId() : null;
    }

    // ── 알람 이벤트 훅 ───────────────────────────────────────────────────────────

    private String buildMessage(AlarmRule rule, Observation observation) {
        String message = rule.getName() + " — " + observation.detail();
        return message.length() <= MAX_MESSAGE_LENGTH ? message : message.substring(0, MAX_MESSAGE_LENGTH);
    }

    /**
     * 알람 이벤트 생성 훅(이슈 #116) — {@link AlarmEventService#recordBreach}가 1차로 멱등을
     * 보장하지만(같은 farm×metricKey 조합 미해결 이벤트 존재 시 no-op), 그 조회-후-저장 자체가
     * 원자적이지 않아 동시 브리치 레이스에서는 DB의 partial unique index(V19, 2차 방어선)가
     * {@link DataIntegrityViolationException}을 던질 수 있다. {@code recordBreach}의 트랜잭션이
     * 이미 rollback-only로 마킹된 지점(그 내부)이 아니라 여기서 잡는다. {@code evaluate()}의 for
     * 루프 바디 전체도 규칙 단위로 격리돼 있어(이슈 #116 리뷰 회귀-A) 이 catch가 없어도 다른 규칙
     * 유실까지는 막히지만, 이 자리에서 먼저 잡아야 "멱등 2차 방어선 흡수"와 "그 밖의 예상 밖 오류"를
     * 로그 레벨(debug vs warn)로 구분할 수 있고, 뒤이은 웹훅 발송도 계속 진행된다.
     *
     * <p>severity는 규칙이 결정한다(#118) — #117까지의 WARNING 고정이 여기서 해소됐다.
     */
    private void recordAlarmBreach(AlarmRule rule, String message) {
        try {
            alarmEventService.recordBreach(rule.getFarmId(), rule.getSeverity(), rule.alarmSourceType(),
                    rule.metricKey(), message, LocalDateTime.now(clock), rule);
        } catch (DataIntegrityViolationException e) {
            log.debug("알람 이벤트 중복 생성(멱등 2차 방어선) — 무시: farmId={}, ruleId={}",
                    rule.getFarmId(), rule.getId());
        }
    }

    /** 테스트 격리용 — 싱글턴 빈이라 통합 테스트 간 상태가 새지 않도록 초기화한다. */
    public void resetState() {
        states.clear();
    }

    /**
     * 특정 농장의 지속시간·쿨다운 상태를 리셋한다 — {@code EnvThresholdService}(§4.6 설정 저장)와
     * {@code AlarmRuleService}(규칙 생성/수정/삭제)가 직후에 호출한다. 변경 전 누적된 이탈 시간을
     * 그대로 이어받으면 새 기준값으로는 방금 시작된 이탈이 곧바로 발동으로 오판정될 수 있어(예:
     * 기준을 널널하게 바꿨다가 다시 좁힐 때), 설정이 바뀐 시점부터 다시 세도록 한다.
     *
     * <p>⚠️ 이 리셋 뒤에도 <b>자동 해소는 계속 동작해야 한다</b>(이슈 #116 리뷰 P2-A) — 그 보장은
     * {@link #evaluateRule}의 무조건 {@code autoResolveIfOpen} 호출에 있다.
     */
    public void resetFarm(Long farmId) {
        states.keySet().removeIf(key -> key.farmId().equals(farmId));
    }

    private record AlertKey(Long farmId, Long ruleId) {
    }

    private static final class AlertState {
        /** 이번 이탈 구간의 최초 관측 시각 — 정상 복귀 시 null로 되돌린다. */
        private final AtomicReference<Instant> firstBreachAt = new AtomicReference<>();
        private final AtomicReference<Instant> lastNotifiedAt = new AtomicReference<>();
    }
}
