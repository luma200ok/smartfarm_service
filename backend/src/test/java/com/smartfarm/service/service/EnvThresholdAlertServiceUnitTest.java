package com.smartfarm.service.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartfarm.service.dto.AiEnvironmentResponse.Indoor;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.entity.FarmEnvThreshold;
import com.smartfarm.service.repository.FarmEnvThresholdRepository;
import com.smartfarm.service.repository.FarmRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link EnvThresholdAlertService} 단위 테스트(contract §4.6) — 연속 2틱 발동·농장×항목×방향별
 * 30분 쿨다운·webhook 미설정/enabled=false 미발송을 검증한다. 쿨다운은 {@link MutableClock}으로
 * 실시간 대기 없이 만료를 재현한다(EnvironmentCache의 package-private TTL 주입 선례와 동일 원칙).
 */
class EnvThresholdAlertServiceUnitTest {

    private static final long FARM_ID = 1L;

    private final FarmEnvThresholdRepository thresholdRepository = mock(FarmEnvThresholdRepository.class);
    private final FarmRepository farmRepository = mock(FarmRepository.class);
    private final EnvThresholdWebhookNotifier notifier = mock(EnvThresholdWebhookNotifier.class);
    private final AlarmEventService alarmEventService = mock(AlarmEventService.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-22T00:00:00Z"));

    private final EnvThresholdAlertService service =
            new EnvThresholdAlertService(thresholdRepository, farmRepository, notifier, alarmEventService, clock);

    private FarmEnvThreshold thresholdEnabled(Double tempMin, Double tempMax) {
        FarmEnvThreshold threshold = FarmEnvThreshold.builder()
                .farmId(FARM_ID)
                .enabled(true)
                .indoorTempMin(tempMin)
                .indoorTempMax(tempMax)
                .build();
        return threshold;
    }

    private Farm farm() {
        return Farm.builder().name("농장").cropType(CropType.TOMATO).build();
    }

    @Test
    @DisplayName("1틱만 이탈하면 발동하지 않는다(연속 2틱 미달)")
    void singleTickDoesNotTrigger() {
        when(thresholdRepository.findEnabledWithWebhookConfigured())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));

        service.evaluate(new Indoor(35.0, 50.0, true)); // 1틱 이탈(상한 초과)

        verify(notifier, never()).notifyBreach(any(), any(), any(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    @DisplayName("연속 2틱 이탈하면 발동한다")
    void twoConsecutiveTicksTrigger() {
        when(thresholdRepository.findEnabledWithWebhookConfigured())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true)); // 1틱
        service.evaluate(new Indoor(36.0, 50.0, true)); // 2틱 — 발동

        verify(notifier, times(1)).notifyBreach(any(), eq(EnvMetric.INDOOR_TEMP), eq(EnvDirection.HIGH),
                eq(36.0), eq(30.0));
    }

    @Test
    @DisplayName("정상 범위로 복귀하면 연속 카운트가 리셋된다")
    void inRangeResetsConsecutiveCount() {
        when(thresholdRepository.findEnabledWithWebhookConfigured())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true)); // 1틱 이탈
        service.evaluate(new Indoor(25.0, 50.0, true)); // 정상 범위 — 리셋
        service.evaluate(new Indoor(35.0, 50.0, true)); // 다시 1틱째(연속 아님)

        verify(notifier, never()).notifyBreach(any(), any(), any(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    @DisplayName("resetFarm 호출 후에는 이전 연속 카운트가 사라져 다음 이탈이 다시 1틱부터 시작한다"
            + "(리뷰 P3 — 설정 변경 직후 EnvThresholdService가 호출)")
    void resetFarmClearsConsecutiveCount() {
        when(thresholdRepository.findEnabledWithWebhookConfigured())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true)); // 1틱 이탈
        service.resetFarm(FARM_ID); // 설정 변경 — 상태 리셋
        service.evaluate(new Indoor(36.0, 50.0, true)); // 리셋 후 다시 1틱째 — 아직 미발동

        verify(notifier, never()).notifyBreach(any(), any(), any(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    @DisplayName("발동 후 30분 쿨다운 내 재이탈은 재발송하지 않는다")
    void cooldownSuppressesReNotification() {
        when(thresholdRepository.findEnabledWithWebhookConfigured())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true)); // 1틱
        service.evaluate(new Indoor(36.0, 50.0, true)); // 2틱 — 발동(1회)
        clock.advance(Duration.ofMinutes(10));
        service.evaluate(new Indoor(37.0, 50.0, true)); // 계속 이탈, 쿨다운 중 — 미발송

        verify(notifier, times(1)).notifyBreach(any(), any(), any(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    @DisplayName("쿨다운(30분) 경과 후 재이탈은 다시 발송한다")
    void reNotifiesAfterCooldownElapses() {
        when(thresholdRepository.findEnabledWithWebhookConfigured())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true)); // 1틱
        service.evaluate(new Indoor(36.0, 50.0, true)); // 2틱 — 발동(1회)
        clock.advance(Duration.ofMinutes(31));
        service.evaluate(new Indoor(37.0, 50.0, true)); // 쿨다운 경과 — 재발송

        verify(notifier, times(2)).notifyBreach(any(), any(), any(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    @DisplayName("enabled=false 농장은 조회 대상에서 이미 제외되므로 평가하지 않는다")
    void disabledFarmsAreExcludedByRepository() {
        when(thresholdRepository.findEnabledWithWebhookConfigured()).thenReturn(List.of());

        service.evaluate(new Indoor(99.0, 99.0, true));

        verify(notifier, never()).notifyBreach(any(), any(), any(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    @DisplayName("indoor가 null(부분 응답)이면 평가 자체를 스킵한다")
    void nullIndoorSkipsEvaluation() {
        service.evaluate(null);

        verify(thresholdRepository, never()).findEnabledWithWebhookConfigured();
    }

    // ── 알람 이벤트 훅(이슈 #116) ────────────────────────────────────

    @Test
    @DisplayName("1틱만 이탈하면 알람 이벤트도 생성하지 않는다(연속 2틱 미달)")
    void singleTickDoesNotRecordAlarmEvent() {
        when(thresholdRepository.findEnabledWithWebhookConfigured())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));

        service.evaluate(new Indoor(35.0, 50.0, true)); // 1틱

        verify(alarmEventService, never()).recordBreach(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("연속 2틱 이탈하면 웹훅 쿨다운과 무관하게 알람 이벤트를 기록한다")
    void twoConsecutiveTicksRecordAlarmEvent() {
        when(thresholdRepository.findEnabledWithWebhookConfigured())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true)); // 1틱
        service.evaluate(new Indoor(36.0, 50.0, true)); // 2틱 — 확정

        verify(alarmEventService, times(1)).recordBreach(eq(FARM_ID), eq(AlarmSeverity.WARNING),
                eq(AlarmSourceType.ENV_THRESHOLD), eq("INDOOR_TEMP_HIGH"), any(), any(), any());
    }

    @Test
    @DisplayName("쿨다운 중 재이탈에도 알람 이벤트 기록은 계속 시도한다(멱등은 AlarmEventService 책임)")
    void alarmEventRecordedEvenDuringWebhookCooldown() {
        when(thresholdRepository.findEnabledWithWebhookConfigured())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true)); // 1틱
        service.evaluate(new Indoor(36.0, 50.0, true)); // 2틱 — 확정(웹훅 발송 1회)
        clock.advance(Duration.ofMinutes(10));
        service.evaluate(new Indoor(37.0, 50.0, true)); // 웹훅은 쿨다운 중이지만 알람 기록은 계속 호출

        verify(alarmEventService, times(2)).recordBreach(eq(FARM_ID), any(), any(),
                eq("INDOOR_TEMP_HIGH"), any(), any(), any());
    }

    @Test
    @DisplayName("정상 범위로 복귀하면 자동 해소를 시도한다")
    void inRangeTriggersAutoResolve() {
        when(thresholdRepository.findEnabledWithWebhookConfigured())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true)); // 1틱 이탈
        service.evaluate(new Indoor(25.0, 50.0, true)); // 정상 복귀

        verify(alarmEventService, times(1)).autoResolveIfOpen(FARM_ID, "INDOOR_TEMP_HIGH");
    }

    @Test
    @DisplayName("이미 정상이던 틱은 자동 해소를 재시도하지 않는다(불필요 조회 방지)")
    void alreadyNormalTickDoesNotRetryAutoResolve() {
        when(thresholdRepository.findEnabledWithWebhookConfigured())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));

        service.evaluate(new Indoor(25.0, 50.0, true)); // 처음부터 정상
        service.evaluate(new Indoor(26.0, 50.0, true)); // 계속 정상

        verify(alarmEventService, never()).autoResolveIfOpen(any(), any());
    }

    /** 30분 쿨다운 만료를 실시간 대기 없이 재현하기 위한 수동 진행 Clock. */
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
