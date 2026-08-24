package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartfarm.service.config.WebhookProperties;
import com.smartfarm.service.dto.AiEnvironmentResponse.Indoor;
import com.smartfarm.service.entity.AlarmComparator;
import com.smartfarm.service.entity.AlarmRule;
import com.smartfarm.service.entity.AlarmRuleSource;
import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.Device;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import com.smartfarm.service.entity.Farm;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

/**
 * {@link EnvThresholdAlertService} 단위 테스트(contract §4.6·§4.13, 이슈 #52 → #116 → #118).
 *
 * <p>두 갈래를 함께 검증한다:
 * <ol>
 *   <li><b>#118 신규</b> — 지속시간 판정 · 지표 소스 3종 라우팅 · 스코프별 독립 알람 · 등급 분화</li>
 *   <li><b>PR #117 불변식 회귀</b> — 규칙 단위 예외 격리 · 멱등성 2차 방어선 흡수 · 정상 틱마다
 *       무조건 자동 해소 · resetFarm 이후에도 자동 해소 지속. (soft delete 농장 제외는 리포지토리
 *       쿼리의 책임이라 이 목킹 테스트로는 원리적으로 검증할 수 없어
 *       {@code AlarmRuleRepositoryIntegrationTest}가 실제 Postgres로 검증한다.)</li>
 * </ol>
 *
 * <p>지속시간·쿨다운 만료는 {@link MutableClock}으로 실시간 대기 없이 재현한다.
 */
class EnvThresholdAlertServiceUnitTest {

    private static final long FARM_ID = 1L;
    private static final long FARM_ID_2 = 2L;

    /**
     * 테스트 전용 지속시간 — "미달에서는 안 울리고 충족되면 울린다"를 폴러 주기(60s)의 배수로 깔끔히
     * 재현하려고 고른 값일 뿐, <b>V20 이관값(60초)과는 무관하다</b>. 이관값의 근거는
     * {@code EnvThresholdService.DERIVED_DURATION_SECONDS}에 있고, 그 값이 실제로 60인지는
     * {@code EnvThresholdDerivedRuleIntegrationTest}·{@code AlarmRuleMigrationV20IntegrationTest}가
     * 검증한다.
     */
    private static final int DURATION_120S = 120;

    private final AlarmRuleRepository alarmRuleRepository = mock(AlarmRuleRepository.class);
    private final FarmRepository farmRepository = mock(FarmRepository.class);
    private final SensorReadingRepository sensorReadingRepository = mock(SensorReadingRepository.class);
    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final AlarmScopeResolver alarmScopeResolver = mock(AlarmScopeResolver.class);
    private final EnvThresholdWebhookNotifier notifier = mock(EnvThresholdWebhookNotifier.class);
    private final AlarmEventService alarmEventService = mock(AlarmEventService.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));

    private final EnvThresholdAlertService service = newService(notifier);

    private EnvThresholdAlertService newService(EnvThresholdWebhookNotifier notifierToUse) {
        // 스코프 대상은 기본적으로 "살아 있다" — 삭제 시나리오(P2-3)만 개별 테스트에서 뒤집는다.
        when(alarmScopeResolver.exists(any(), any(), any())).thenReturn(true);
        return new EnvThresholdAlertService(alarmRuleRepository, farmRepository, sensorReadingRepository,
                deviceRepository, alarmScopeResolver, notifierToUse, alarmEventService, clock);
    }

    /**
     * 규칙 id는 {@link AlarmRule#metricKey()}(멱등성 키)의 근거라 목킹 테스트에서도 반드시 채워야
     * 한다 — 영속화를 거치지 않으므로 리플렉션으로 주입한다(SensorSimulatorServiceTest 선례).
     */
    private AlarmRule rule(long id, long farmId, AlarmRuleSource source, String metric,
                            AlarmComparator comparator, Double value, AlarmSeverity severity,
                            AlarmScopeType scopeType, Long scopeId) {
        AlarmRule rule = AlarmRule.builder()
                .farmId(farmId)
                .name("규칙" + id)
                .enabled(true)
                .source(source)
                .metric(metric)
                .comparator(comparator)
                .thresholdValue(value)
                .durationSeconds(DURATION_120S)
                .severity(severity)
                .scopeType(scopeType)
                .scopeId(scopeId)
                .build();
        ReflectionTestUtils.setField(rule, "id", id);
        return rule;
    }

    /** 실내 온도 상한(GT) 규칙 — #117 테스트의 {@code thresholdEnabled(20, 30)} 상한 방향에 대응. */
    private AlarmRule tempMaxRule(long id, long farmId, double max) {
        return rule(id, farmId, AlarmRuleSource.ENV_SNAPSHOT, "INDOOR_TEMP", AlarmComparator.GT, max,
                AlarmSeverity.WARNING, AlarmScopeType.FARM, null);
    }

    private Farm farm() {
        return Farm.builder().name("농장").cropType(CropType.TOMATO).build();
    }

    /** 지속시간 게이트를 넘기기 위해 시계를 진행시키며 같은 값을 다시 관측한다. */
    private void tick(Indoor indoor, Duration advance) {
        clock.advance(advance);
        service.evaluate(indoor);
    }

    // ── 지속시간 판정(#118) ─────────────────────────────────────────────────────

    @Test
    @DisplayName("지속시간 미달이면 발동하지 않는다(최초 이탈 관측 직후)")
    void breachShorterThanDurationDoesNotTrigger() {
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(tempMaxRule(10L, FARM_ID, 30.0)));

        service.evaluate(new Indoor(35.0, 50.0, true));           // 최초 이탈 — 경과 0초
        tick(new Indoor(36.0, 50.0, true), Duration.ofSeconds(60)); // 경과 60초 < 120초

        verify(notifier, never()).notifyBreach(any(), any(), anyString());
        verify(alarmEventService, never()).recordBreach(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("지속시간을 채우면 발동한다(duration_seconds 기반 — 틱 수가 아니라 경과 시간)")
    void breachLongerThanDurationTriggers() {
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(tempMaxRule(10L, FARM_ID, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true));
        tick(new Indoor(36.0, 50.0, true), Duration.ofSeconds(120)); // 경과 120초 >= 120초 — 발동

        verify(alarmEventService, times(1)).recordBreach(eq(FARM_ID), eq(AlarmSeverity.WARNING),
                eq(AlarmSourceType.ENV_THRESHOLD), eq("RULE_10"), any(), any(), any());
        verify(notifier, times(1)).notifyBreach(any(), eq(AlarmSeverity.WARNING), anyString());
    }

    @Test
    @DisplayName("폴링 간격이 길어져도(1틱 = 180초) 지속시간만 채우면 발동한다 — 틱 수 하드코딩 제거 확인")
    void singleLongTickSatisfiesDuration() {
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(tempMaxRule(10L, FARM_ID, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true));
        tick(new Indoor(36.0, 50.0, true), Duration.ofSeconds(180));

        verify(alarmEventService, times(1)).recordBreach(eq(FARM_ID), any(), any(), eq("RULE_10"),
                any(), any(), any());
    }

    @Test
    @DisplayName("정상 범위로 복귀하면 누적된 이탈 시간이 사라져 다음 이탈이 처음부터 다시 시작한다")
    void inRangeResetsElapsedBreachTime() {
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(tempMaxRule(10L, FARM_ID, 30.0)));

        service.evaluate(new Indoor(35.0, 50.0, true));            // 이탈 시작
        tick(new Indoor(25.0, 50.0, true), Duration.ofSeconds(60)); // 정상 복귀 — 리셋
        tick(new Indoor(35.0, 50.0, true), Duration.ofSeconds(60)); // 다시 이탈 시작(경과 0초)
        tick(new Indoor(35.0, 50.0, true), Duration.ofSeconds(60)); // 경과 60초 < 120초

        verify(notifier, never()).notifyBreach(any(), any(), anyString());
    }

    @Test
    @DisplayName("resetFarm 호출 후에는 누적 이탈 시간이 사라져 다음 이탈이 처음부터 다시 시작한다"
            + "(설정·규칙 변경 직후 EnvThresholdService·AlarmRuleService가 호출)")
    void resetFarmClearsElapsedBreachTime() {
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(tempMaxRule(10L, FARM_ID, 30.0)));

        service.evaluate(new Indoor(35.0, 50.0, true));
        service.resetFarm(FARM_ID);                                 // 설정 변경 — 상태 리셋
        tick(new Indoor(36.0, 50.0, true), Duration.ofSeconds(120)); // 리셋 후 다시 경과 0초
        tick(new Indoor(36.0, 50.0, true), Duration.ofSeconds(60));  // 경과 60초 < 120초 — 아직 미발동

        verify(notifier, never()).notifyBreach(any(), any(), anyString());
    }

    // ── 쿨다운(§4.6) ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("발동 후 30분 쿨다운 내 재이탈은 재발송하지 않는다")
    void cooldownSuppressesReNotification() {
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(tempMaxRule(10L, FARM_ID, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true));
        tick(new Indoor(36.0, 50.0, true), Duration.ofSeconds(120)); // 발동(1회)
        tick(new Indoor(37.0, 50.0, true), Duration.ofMinutes(10));  // 계속 이탈, 쿨다운 중 — 미발송

        verify(notifier, times(1)).notifyBreach(any(), any(), anyString());
    }

    @Test
    @DisplayName("쿨다운(30분) 경과 후 재이탈은 다시 발송한다")
    void reNotifiesAfterCooldownElapses() {
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(tempMaxRule(10L, FARM_ID, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true));
        tick(new Indoor(36.0, 50.0, true), Duration.ofSeconds(120)); // 발동(1회)
        tick(new Indoor(37.0, 50.0, true), Duration.ofMinutes(31));  // 쿨다운 경과 — 재발송

        verify(notifier, times(2)).notifyBreach(any(), any(), anyString());
    }

    @Test
    @DisplayName("보안 P3-3: resetFarm은 지속시간 상태만 지우고 30분 웹훅 쿨다운은 보존한다 — "
            + "이탈 중인 규칙에 PATCH를 반복해 쿨다운을 우회하고 틱마다 외부 발송을 유발할 수 없어야 한다")
    void resetFarmPreservesWebhookCooldown() {
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(tempMaxRule(10L, FARM_ID, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true));
        tick(new Indoor(36.0, 50.0, true), Duration.ofSeconds(120)); // 발동 — 웹훅 1회

        // 규칙 PATCH를 흉내내 반복 리셋한 뒤, 매번 지속시간을 다시 채워 발동시킨다.
        for (int i = 0; i < 3; i++) {
            service.resetFarm(FARM_ID);
            service.evaluate(new Indoor(36.0, 50.0, true));
            tick(new Indoor(36.0, 50.0, true), Duration.ofSeconds(120));
        }

        // 쿨다운(30분)이 보존되므로 추가 발송은 없다. 상태를 통째로 지우던 옛 구현이면 4회가 된다.
        verify(notifier, times(1)).notifyBreach(any(), any(), anyString());
    }

    @Test
    @DisplayName("forgetRule은 그 규칙 상태를 통째로 버린다(삭제된 규칙 — 보존할 쿨다운이 없다)")
    void forgetRuleDropsCooldownToo() {
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(tempMaxRule(10L, FARM_ID, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true));
        tick(new Indoor(36.0, 50.0, true), Duration.ofSeconds(120)); // 발동 — 웹훅 1회

        service.forgetRule(FARM_ID, 10L); // 규칙 삭제(같은 id로 재생성되지 않는 것이 정상)
        service.evaluate(new Indoor(36.0, 50.0, true));
        tick(new Indoor(36.0, 50.0, true), Duration.ofSeconds(120));

        verify(notifier, times(2)).notifyBreach(any(), any(), anyString());
    }

    @Test
    @DisplayName("쿨다운 중 재이탈에도 알람 이벤트 기록은 계속 시도한다(멱등은 AlarmEventService 책임)")
    void alarmEventRecordedEvenDuringWebhookCooldown() {
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(tempMaxRule(10L, FARM_ID, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true));
        tick(new Indoor(36.0, 50.0, true), Duration.ofSeconds(120)); // 발동(웹훅 1회)
        tick(new Indoor(37.0, 50.0, true), Duration.ofMinutes(10));  // 웹훅은 쿨다운 중

        verify(alarmEventService, times(2)).recordBreach(eq(FARM_ID), any(), any(), eq("RULE_10"),
                any(), any(), any());
    }

    // ── 평가 대상·관측 부재 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("enabled=false 규칙은 조회 대상에서 이미 제외되므로 평가하지 않는다")
    void disabledRulesAreExcludedByRepository() {
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of());

        service.evaluate(new Indoor(99.0, 99.0, true));

        verify(notifier, never()).notifyBreach(any(), any(), anyString());
    }

    @Test
    @DisplayName("indoor가 null(ai-server 부분 응답)이어도 ENV_SNAPSHOT 규칙만 건너뛴다 — 자동 해소도"
            + " 하지 않는다(관측 부재는 정상 복귀가 아니다)")
    void nullIndoorSkipsOnlyEnvSnapshotRules() {
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(tempMaxRule(10L, FARM_ID, 30.0)));

        service.evaluate(null);

        verify(alarmEventService, never()).autoResolveIfOpen(anyLong(), anyString());
        verify(alarmEventService, never()).recordBreach(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("P1-1: 관측 부재 틱(indoor=null)은 누적 이탈 시간을 리셋하지 않는다 — ai-server 장애나 "
            + "중복 tick으로 폴러가 indoor를 못 실어 보내도, 관측이 재개되면 이탈 구간을 이어서 센다"
            + "(리셋되면 ai-server가 불안정한 동안 ENV_SNAPSHOT 알람이 영영 발동하지 못한다)")
    void observationGapDoesNotResetElapsedBreachTime() {
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(tempMaxRule(10L, FARM_ID, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true));            // 이탈 시작(경과 0초)
        tick(null, Duration.ofSeconds(60));                        // 관측 부재 — 상태 무변경이어야 한다
        tick(null, Duration.ofSeconds(60));                        // 관측 부재
        tick(new Indoor(36.0, 50.0, true), Duration.ofSeconds(0));  // 관측 재개, 최초 이탈로부터 120초

        // firstBreachAt이 리셋됐다면 여기서 경과 0초라 발동하지 않는다.
        verify(alarmEventService, times(1)).recordBreach(eq(FARM_ID), any(), any(), eq("RULE_10"),
                any(), any(), any());
    }

    @Test
    @DisplayName("indoor가 null이어도 센서 규칙 평가는 계속된다(#117까지는 여기서 즉시 return했다)")
    void nullIndoorStillEvaluatesSensorRules() {
        AlarmRule sensorRule = rule(20L, FARM_ID, AlarmRuleSource.SENSOR_READING, SensorMetric.EC.name(),
                AlarmComparator.GT, 2.8, AlarmSeverity.CRITICAL, AlarmScopeType.FARM, null);
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(sensorRule));
        when(sensorReadingRepository.findLatestInScope(eq(FARM_ID), eq("EC"), any(), any(), any(), any()))
                .thenReturn(List.of(latest(2.0)));

        service.evaluate(null);

        // 정상 범위이므로 발동은 없지만, 평가 자체는 수행돼 자동 해소가 호출된다.
        verify(alarmEventService, times(1)).autoResolveIfOpen(FARM_ID, "RULE_20");
    }

    // ── 지표 소스 라우팅(#118) ──────────────────────────────────────────────────

    @Test
    @DisplayName("SENSOR_READING 규칙은 sensor_readings 스코프 최신값으로 판정한다(프리뷰 '급액 EC > 2.8')")
    void sensorReadingRuleUsesScopedLatestValue() {
        AlarmRule ecRule = rule(20L, FARM_ID, AlarmRuleSource.SENSOR_READING, SensorMetric.EC.name(),
                AlarmComparator.GT, 2.8, AlarmSeverity.CRITICAL, AlarmScopeType.LEVEL, 77L);
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(ecRule));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));
        when(sensorReadingRepository.findLatestInScope(eq(FARM_ID), eq("EC"), any(), isNull(), isNull(), eq(77L)))
                .thenReturn(List.of(latest(3.1)));

        service.evaluate(null);
        clock.advance(Duration.ofSeconds(120));
        service.evaluate(null);

        verify(alarmEventService, times(1)).recordBreach(eq(FARM_ID), eq(AlarmSeverity.CRITICAL),
                eq(AlarmSourceType.SENSOR_THRESHOLD), eq("RULE_20"), any(), any(), any());
    }

    @Test
    @DisplayName("신선한 측정값이 없으면 관측 부재로 보고 발동도 자동 해소도 하지 않는다")
    void staleSensorScopeIsTreatedAsNoObservation() {
        AlarmRule ecRule = rule(20L, FARM_ID, AlarmRuleSource.SENSOR_READING, SensorMetric.EC.name(),
                AlarmComparator.GT, 2.8, AlarmSeverity.CRITICAL, AlarmScopeType.FARM, null);
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(ecRule));
        when(sensorReadingRepository.findLatestInScope(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.evaluate(null);
        clock.advance(Duration.ofSeconds(300));
        service.evaluate(null);

        verify(alarmEventService, never()).recordBreach(any(), any(), any(), any(), any(), any(), any());
        verify(alarmEventService, never()).autoResolveIfOpen(anyLong(), anyString());
    }

    @Test
    @DisplayName("DEVICE_HEARTBEAT 규칙은 스코프 안 OFFLINE 장비를 무응답으로 판정한다"
            + "(프리뷰 '게이트웨이 응답 없음 · 3분 지속')")
    void deviceHeartbeatRuleDetectsOfflineDevice() {
        AlarmRule heartbeatRule = rule(30L, FARM_ID, AlarmRuleSource.DEVICE_HEARTBEAT, null,
                AlarmComparator.ABSENT, null, AlarmSeverity.CRITICAL, AlarmScopeType.ZONE, 9L);
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(heartbeatRule));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));
        when(deviceRepository.findByFarmIdAndZoneIdOrderByIdAsc(FARM_ID, 9L))
                .thenReturn(List.of(device("게이트웨이-1", DeviceStatus.OFFLINE)));

        service.evaluate(null);
        clock.advance(Duration.ofSeconds(120));
        service.evaluate(null);

        verify(alarmEventService, times(1)).recordBreach(eq(FARM_ID), eq(AlarmSeverity.CRITICAL),
                eq(AlarmSourceType.DEVICE_HEARTBEAT), eq("RULE_30"), any(), any(), any());
    }

    @Test
    @DisplayName("스코프의 장비가 전부 정상이면 자동 해소를 시도한다")
    void healthyDevicesTriggerAutoResolve() {
        AlarmRule heartbeatRule = rule(30L, FARM_ID, AlarmRuleSource.DEVICE_HEARTBEAT, null,
                AlarmComparator.ABSENT, null, AlarmSeverity.CRITICAL, AlarmScopeType.ZONE, 9L);
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(heartbeatRule));
        when(deviceRepository.findByFarmIdAndZoneIdOrderByIdAsc(FARM_ID, 9L))
                .thenReturn(List.of(device("게이트웨이-1", DeviceStatus.NORMAL)));

        service.evaluate(null);

        verify(alarmEventService, times(1)).autoResolveIfOpen(FARM_ID, "RULE_30");
    }

    @Test
    @DisplayName("스코프에 활성 장비가 없으면 관측 부재로 보고 자동 해소하지 않는다"
            + "(장비를 모두 지운 존에서 열린 알람이 근거 없이 닫히면 안 된다)")
    void emptyDeviceScopeIsTreatedAsNoObservation() {
        AlarmRule heartbeatRule = rule(30L, FARM_ID, AlarmRuleSource.DEVICE_HEARTBEAT, null,
                AlarmComparator.ABSENT, null, AlarmSeverity.CRITICAL, AlarmScopeType.ZONE, 9L);
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(heartbeatRule));
        when(deviceRepository.findByFarmIdAndZoneIdOrderByIdAsc(FARM_ID, 9L)).thenReturn(List.of());

        service.evaluate(null);

        verify(alarmEventService, never()).autoResolveIfOpen(anyLong(), anyString());
    }

    // ── 스코프 대상 삭제(#118 리뷰 P2-3) ───────────────────────────────────────

    @Test
    @DisplayName("P2-3: 스코프 대상(랙)이 soft delete되면 열린 알람을 자동 해소하고 그 규칙은 "
            + "건너뛴다 — 측정값 조회조차 하지 않는다(삭제된 랙의 rack_level_id IS NULL 읽기가 "
            + "집계에 섞이는 경로도 함께 막힌다)")
    void deletedScopeTargetResolvesAlarmAndSkipsRule() {
        AlarmRule rackRule = rule(60L, FARM_ID, AlarmRuleSource.SENSOR_READING, SensorMetric.EC.name(),
                AlarmComparator.GT, 2.8, AlarmSeverity.WARNING, AlarmScopeType.RACK, 100L);
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(rackRule));
        when(alarmScopeResolver.exists(FARM_ID, AlarmScopeType.RACK, 100L)).thenReturn(false);

        service.evaluate(null);

        verify(alarmEventService, times(1)).autoResolveIfOpen(FARM_ID, "RULE_60");
        verify(sensorReadingRepository, never()).findLatestInScope(any(), any(), any(), any(), any(), any());
        verify(alarmEventService, never()).recordBreach(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("P2-3: 스코프 대상이 살아 있는데 그 안이 비었을 뿐이면(관측 부재) 열린 알람을 "
            + "유지한다 — '비었다'와 '사라졌다'는 다른 상태다")
    void aliveButEmptyScopeKeepsOpenAlarm() {
        AlarmRule heartbeatRule = rule(61L, FARM_ID, AlarmRuleSource.DEVICE_HEARTBEAT, null,
                AlarmComparator.ABSENT, null, AlarmSeverity.CRITICAL, AlarmScopeType.ZONE, 9L);
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(heartbeatRule));
        when(alarmScopeResolver.exists(FARM_ID, AlarmScopeType.ZONE, 9L)).thenReturn(true);
        when(deviceRepository.findByFarmIdAndZoneIdOrderByIdAsc(FARM_ID, 9L)).thenReturn(List.of());

        service.evaluate(null);

        verify(alarmEventService, never()).autoResolveIfOpen(anyLong(), anyString());
    }

    // ── 스코프별 독립 알람 · 등급 분화(#118) ────────────────────────────────────

    @Test
    @DisplayName("같은 지표라도 스코프가 다르면 서로 다른 멱등성 키로 독립 알람이 생성된다"
            + "(V19 partial unique index가 한쪽을 조용히 삼키면 안 된다)")
    void sameMetricDifferentScopesProduceIndependentAlarms() {
        AlarmRule rackA = rule(41L, FARM_ID, AlarmRuleSource.SENSOR_READING, SensorMetric.EC.name(),
                AlarmComparator.GT, 2.8, AlarmSeverity.WARNING, AlarmScopeType.RACK, 100L);
        AlarmRule rackB = rule(42L, FARM_ID, AlarmRuleSource.SENSOR_READING, SensorMetric.EC.name(),
                AlarmComparator.GT, 2.8, AlarmSeverity.WARNING, AlarmScopeType.RACK, 200L);
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(rackA, rackB));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));
        when(sensorReadingRepository.findLatestInScope(eq(FARM_ID), eq("EC"), any(), isNull(), eq(100L), isNull()))
                .thenReturn(List.of(latest(3.5)));
        when(sensorReadingRepository.findLatestInScope(eq(FARM_ID), eq("EC"), any(), isNull(), eq(200L), isNull()))
                .thenReturn(List.of(latest(3.9)));

        service.evaluate(null);
        clock.advance(Duration.ofSeconds(120));
        service.evaluate(null);

        verify(alarmEventService, times(1)).recordBreach(eq(FARM_ID), any(), any(), eq("RULE_41"),
                any(), any(), any());
        verify(alarmEventService, times(1)).recordBreach(eq(FARM_ID), any(), any(), eq("RULE_42"),
                any(), any(), any());
    }

    @Test
    @DisplayName("규칙의 severity가 알람 이벤트 등급이 된다(#116의 WARNING 고정 해소)")
    void ruleSeverityDecidesEventSeverity() {
        AlarmRule criticalRule = rule(50L, FARM_ID, AlarmRuleSource.ENV_SNAPSHOT, "INDOOR_HUMIDITY",
                AlarmComparator.GT, 90.0, AlarmSeverity.CRITICAL, AlarmScopeType.FARM, null);
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(criticalRule));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(25.0, 95.0, true));
        tick(new Indoor(25.0, 95.0, true), Duration.ofSeconds(120));

        verify(alarmEventService, times(1)).recordBreach(eq(FARM_ID), eq(AlarmSeverity.CRITICAL),
                any(), eq("RULE_50"), any(), any(), any());
    }

    // ── PR #116/#117 불변식 회귀 ────────────────────────────────────────────────

    @Test
    @DisplayName("P2-B: 웹훅 URL 미설정 농장도 평가 대상에 포함돼 알람 이벤트는 정상 기록되지만"
            + " 실제 웹훅 HTTP 요청은 발송되지 않는다(이슈 #116 리뷰)")
    void alarmEventRecordedWithoutWebhookConfigButNoActualHttpRequestSent() {
        // notifier를 Mockito mock 대신 실제 구현체로 둬서(RestClient만 mock) webhookUrl==null일 때
        // 실제로 HTTP 요청을 시도하지 않는지까지 관찰한다 — 클래스 필드 notifier(mock)로는 내부
        // no-op 여부를 확인할 수 없다.
        RestClient webhookRestClient = mock(RestClient.class);
        EnvThresholdWebhookNotifier realNotifier = new EnvThresholdWebhookNotifier(
                new WebhookProperties(Duration.ofSeconds(5), "https://farm.luma200ok.com"), webhookRestClient);
        EnvThresholdAlertService serviceWithRealNotifier = newService(realNotifier);

        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(tempMaxRule(10L, FARM_ID, 30.0)));
        // farm()은 webhookUrl을 설정하지 않아 기본값 null — 웹훅 미설정 농장.
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        serviceWithRealNotifier.evaluate(new Indoor(35.0, 50.0, true));
        clock.advance(Duration.ofSeconds(120));
        serviceWithRealNotifier.evaluate(new Indoor(36.0, 50.0, true));

        verify(alarmEventService, times(1)).recordBreach(eq(FARM_ID), eq(AlarmSeverity.WARNING),
                eq(AlarmSourceType.ENV_THRESHOLD), eq("RULE_10"), any(), any(), any());
        verifyNoInteractions(webhookRestClient);
    }

    @Test
    @DisplayName("보안 P3-2: 웹훅 본문의 @everyone은 멘션으로 해석되지 않는다 — payload에 "
            + "allowed_mentions.parse=[]가 실린다(규칙 이름은 사용자 입력이라 멘션 폭탄 벡터다)")
    void webhookPayloadSuppressesMentions() {
        RestClient webhookRestClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(webhookRestClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        EnvThresholdWebhookNotifier realNotifier = new EnvThresholdWebhookNotifier(
                new WebhookProperties(Duration.ofSeconds(5), "https://farm.luma200ok.com"), webhookRestClient);
        Farm farmWithWebhook = farm();
        ReflectionTestUtils.setField(farmWithWebhook, "webhookUrl",
                "https://discord.com/api/webhooks/1/token");

        realNotifier.notifyBreach(farmWithWebhook, AlarmSeverity.CRITICAL, "@everyone 급액 EC 경보 — 이탈");

        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(bodySpec).body(bodyCaptor.capture());
        String payload = bodyCaptor.getValue().toString();
        assertThat(payload).contains("@everyone");        // 문자열 자체는 그대로 보인다
        assertThat(payload).contains("allowedMentions");  // 억제 필드가 실려 있다
        assertThat(payload).contains("parse=[]");         // 어떤 멘션도 해석하지 않는다
    }

    @Test
    @DisplayName("정상 범위로 복귀하면 자동 해소를 시도한다")
    void inRangeTriggersAutoResolve() {
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(tempMaxRule(10L, FARM_ID, 30.0)));

        service.evaluate(new Indoor(35.0, 50.0, true)); // 이탈
        tick(new Indoor(25.0, 50.0, true), Duration.ofSeconds(60)); // 정상 복귀

        verify(alarmEventService, times(1)).autoResolveIfOpen(FARM_ID, "RULE_10");
    }

    @Test
    @DisplayName("P1-B: 멱등성 2차 방어선(partial unique index) 위반은 스케줄러 틱을 끊지 않고 흡수한다"
            + "(recordBreach가 DataIntegrityViolationException을 던져도 evaluate는 정상 완료하고 "
            + "웹훅 발송까지 이어진다)")
    void dataIntegrityViolationOnRecordBreachIsSwallowed() {
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(tempMaxRule(10L, FARM_ID, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));
        doThrow(new DataIntegrityViolationException("ux_alarm_events_open_farm_metric 위반(레이스 가정)"))
                .when(alarmEventService).recordBreach(eq(FARM_ID), any(), any(), eq("RULE_10"),
                        any(), any(), any());

        assertThatCode(() -> {
            service.evaluate(new Indoor(35.0, 50.0, true));
            tick(new Indoor(36.0, 50.0, true), Duration.ofSeconds(120));
        }).doesNotThrowAnyException();

        // 알람 이벤트 저장은 실패했지만, 뒤이은 웹훅 발송(같은 evaluateRule 호출)은 정상 진행돼야
        // 한다 — recordAlarmBreach의 catch가 그 이후 로직을 끊지 않는다는 뜻.
        verify(notifier, times(1)).notifyBreach(any(), eq(AlarmSeverity.WARNING), anyString());
    }

    @Test
    @DisplayName("P2-A: 이미 정상이던 틱도 매번 자동 해소를 시도한다(인메모리 상태만으로 열린 알람"
            + " 없음을 단정할 수 없음 — 이슈 #116 리뷰)")
    void alreadyNormalTickStillAttemptsAutoResolve() {
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(tempMaxRule(10L, FARM_ID, 30.0)));

        service.evaluate(new Indoor(25.0, 50.0, true)); // 처음부터 정상
        tick(new Indoor(26.0, 50.0, true), Duration.ofSeconds(60)); // 계속 정상

        // autoResolveIfOpen 자체가 열린 이벤트 없으면 no-op이라 매 정상 틱마다 호출해도 안전하다 —
        // 이 무조건 호출이 P2-A의 핵심 수정이다.
        verify(alarmEventService, times(2)).autoResolveIfOpen(FARM_ID, "RULE_10");
    }

    @Test
    @DisplayName("P2-A: resetFarm으로 인메모리 상태가 초기화된 뒤에도 정상 틱이 오면 DB의 열린 이벤트를"
            + " 자동 해소한다(설정 저장마다 resetFarm이 호출되므로 유령 알람이 고착되면 안 된다)")
    void autoResolveStillHappensAfterResetFarmClearsState() {
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(tempMaxRule(10L, FARM_ID, 30.0)));

        service.evaluate(new Indoor(35.0, 50.0, true)); // 이탈(DB에 열린 이벤트가 있다고 가정)
        service.resetFarm(FARM_ID);                     // 설정 저장 — 인메모리 상태 소멸
        tick(new Indoor(25.0, 50.0, true), Duration.ofSeconds(60)); // 리셋 후 첫 정상 틱

        verify(alarmEventService, times(1)).autoResolveIfOpen(FARM_ID, "RULE_10");
    }

    @Test
    @DisplayName("회귀-A: 한 규칙의 autoResolveIfOpen이 낙관적 락 충돌로 예외를 던져도 evaluate()는 "
            + "멈추지 않고 뒤 순서 규칙 평가를 계속 진행한다(evaluate() 루프 바디의 규칙 단위 격리)")
    void oneRuleFailureDoesNotBlockLaterRules() {
        AlarmRule failing = tempMaxRule(10L, FARM_ID, 30.0);   // 정상 범위 유지 → autoResolve 경로
        AlarmRule later = tempMaxRule(11L, FARM_ID_2, 24.0);   // 같은 값에 이탈하도록 좁힌 규칙
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(failing, later));
        when(farmRepository.findById(FARM_ID_2)).thenReturn(Optional.of(farm()));
        doThrow(new ObjectOptimisticLockingFailureException("AlarmEvent", FARM_ID))
                .when(alarmEventService).autoResolveIfOpen(eq(FARM_ID), any());

        assertThatCode(() -> {
            service.evaluate(new Indoor(25.0, 50.0, true)); // rule10 예외 흡수, rule11 이탈 시작
            tick(new Indoor(25.0, 50.0, true), Duration.ofSeconds(120)); // rule11 지속시간 충족
        }).doesNotThrowAnyException();

        verify(alarmEventService, times(1)).recordBreach(eq(FARM_ID_2), any(), any(), eq("RULE_11"),
                any(), any(), any());
    }

    @Test
    @DisplayName("회귀-A 확장: 데이터 소스 조회(sensor_readings) 실패도 뒤 순서 규칙을 끊지 않는다"
            + "(#118에서 늘어난 실패 경로)")
    void sensorQueryFailureDoesNotBlockLaterRules() {
        AlarmRule failingSensorRule = rule(20L, FARM_ID, AlarmRuleSource.SENSOR_READING,
                SensorMetric.EC.name(), AlarmComparator.GT, 2.8, AlarmSeverity.WARNING,
                AlarmScopeType.FARM, null);
        AlarmRule later = tempMaxRule(11L, FARM_ID_2, 24.0);
        when(alarmRuleRepository.findEnabled()).thenReturn(List.of(failingSensorRule, later));
        when(farmRepository.findById(FARM_ID_2)).thenReturn(Optional.of(farm()));
        when(sensorReadingRepository.findLatestInScope(any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("측정값 조회 실패(가정)"));

        assertThatCode(() -> {
            service.evaluate(new Indoor(25.0, 50.0, true));
            tick(new Indoor(25.0, 50.0, true), Duration.ofSeconds(120));
        }).doesNotThrowAnyException();

        verify(alarmEventService, times(1)).recordBreach(eq(FARM_ID_2), any(), any(), eq("RULE_11"),
                any(), any(), any());
    }

    // ── 테스트 픽스처 ───────────────────────────────────────────────────────────

    private ReadingScopeLatestProjection latest(double value) {
        return new ReadingScopeLatestProjection() {
            @Override
            public Double getValue() {
                return value;
            }

            @Override
            public LocalDateTime getMeasuredAt() {
                return LocalDateTime.now(clock);
            }
        };
    }

    private Device device(String name, DeviceStatus status) {
        return Device.builder()
                .farmId(FARM_ID)
                .zoneId(9L)
                .name(name)
                .kind(DeviceKind.GATEWAY)
                .status(status)
                .build();
    }

    /** 지속시간·쿨다운 만료를 실시간 대기 없이 재현하기 위한 수동 진행 Clock. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
