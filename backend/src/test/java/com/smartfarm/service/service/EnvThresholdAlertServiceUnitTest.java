package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartfarm.service.config.WebhookProperties;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.client.RestClient;

/**
 * {@link EnvThresholdAlertService} 단위 테스트(contract §4.6) — 연속 2틱 발동·농장×항목×방향별
 * 30분 쿨다운·webhook 미설정/enabled=false 미발송을 검증한다. 쿨다운은 {@link MutableClock}으로
 * 실시간 대기 없이 만료를 재현한다(EnvironmentCache의 package-private TTL 주입 선례와 동일 원칙).
 */
class EnvThresholdAlertServiceUnitTest {

    private static final long FARM_ID = 1L;
    private static final long FARM_ID_2 = 2L;

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
        when(thresholdRepository.findEnabled())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));

        service.evaluate(new Indoor(35.0, 50.0, true)); // 1틱 이탈(상한 초과)

        verify(notifier, never()).notifyBreach(any(), any(), any(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    @DisplayName("연속 2틱 이탈하면 발동한다")
    void twoConsecutiveTicksTrigger() {
        when(thresholdRepository.findEnabled())
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
        when(thresholdRepository.findEnabled())
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
        when(thresholdRepository.findEnabled())
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
        when(thresholdRepository.findEnabled())
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
        when(thresholdRepository.findEnabled())
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
        when(thresholdRepository.findEnabled()).thenReturn(List.of());

        service.evaluate(new Indoor(99.0, 99.0, true));

        verify(notifier, never()).notifyBreach(any(), any(), any(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    @DisplayName("indoor가 null(부분 응답)이면 평가 자체를 스킵한다")
    void nullIndoorSkipsEvaluation() {
        service.evaluate(null);

        verify(thresholdRepository, never()).findEnabled();
    }

    // ── 알람 이벤트 훅(이슈 #116) ────────────────────────────────────

    @Test
    @DisplayName("1틱만 이탈하면 알람 이벤트도 생성하지 않는다(연속 2틱 미달)")
    void singleTickDoesNotRecordAlarmEvent() {
        when(thresholdRepository.findEnabled())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));

        service.evaluate(new Indoor(35.0, 50.0, true)); // 1틱

        verify(alarmEventService, never()).recordBreach(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("연속 2틱 이탈하면 웹훅 쿨다운과 무관하게 알람 이벤트를 기록한다")
    void twoConsecutiveTicksRecordAlarmEvent() {
        when(thresholdRepository.findEnabled())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true)); // 1틱
        service.evaluate(new Indoor(36.0, 50.0, true)); // 2틱 — 확정

        verify(alarmEventService, times(1)).recordBreach(eq(FARM_ID), eq(AlarmSeverity.WARNING),
                eq(AlarmSourceType.ENV_THRESHOLD), eq("INDOOR_TEMP_HIGH"), any(), any(), any());
    }

    @Test
    @DisplayName("P2-B: 웹훅 URL 미설정 농장도 임계치 평가 대상에 포함돼 알람 이벤트는 정상 기록되지만"
            + " 실제 웹훅 HTTP 요청은 발송되지 않는다(이슈 #116 리뷰 — findEnabled()가 웹훅 여부와"
            + " 무관하게 enabled=true 전체를 대상으로 삼도록 바뀜, 예전 findEnabledWithWebhookConfigured"
            + "였다면 이 농장은 평가 대상에서 아예 제외돼 알람 이벤트가 0건이었을 것)")
    void alarmEventRecordedWithoutWebhookConfigButNoActualHttpRequestSent() {
        // notifier를 Mockito mock 대신 실제 구현체로 둬서(RestClient만 mock) webhookUrl==null일 때
        // 실제로 HTTP 요청을 시도하지 않는지까지 관찰한다 — 클래스 필드 notifier(mock)로는 내부
        // no-op 여부를 확인할 수 없다.
        RestClient webhookRestClient = mock(RestClient.class);
        EnvThresholdWebhookNotifier realNotifier = new EnvThresholdWebhookNotifier(
                new WebhookProperties(Duration.ofSeconds(5), "https://farm.luma200ok.com"), webhookRestClient);
        EnvThresholdAlertService serviceWithRealNotifier = new EnvThresholdAlertService(
                thresholdRepository, farmRepository, realNotifier, alarmEventService, clock);

        when(thresholdRepository.findEnabled())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));
        // farm()은 webhookUrl을 설정하지 않아 기본값 null — 웹훅 미설정 농장.
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        serviceWithRealNotifier.evaluate(new Indoor(35.0, 50.0, true)); // 1틱
        serviceWithRealNotifier.evaluate(new Indoor(36.0, 50.0, true)); // 2틱 — 확정

        // 알람 이벤트는 웹훅 설정과 무관하게 기록된다.
        verify(alarmEventService, times(1)).recordBreach(eq(FARM_ID), eq(AlarmSeverity.WARNING),
                eq(AlarmSourceType.ENV_THRESHOLD), eq("INDOOR_TEMP_HIGH"), any(), any(), any());
        // 웹훅 URL이 없으므로 EnvThresholdWebhookNotifier가 즉시 return하고, RestClient는 전혀
        // 호출되지 않는다(실제 HTTP 요청 미발송).
        verifyNoInteractions(webhookRestClient);
    }

    @Test
    @DisplayName("쿨다운 중 재이탈에도 알람 이벤트 기록은 계속 시도한다(멱등은 AlarmEventService 책임)")
    void alarmEventRecordedEvenDuringWebhookCooldown() {
        when(thresholdRepository.findEnabled())
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
        when(thresholdRepository.findEnabled())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));

        service.evaluate(new Indoor(35.0, 50.0, true)); // 1틱 이탈
        service.evaluate(new Indoor(25.0, 50.0, true)); // 정상 복귀

        verify(alarmEventService, times(1)).autoResolveIfOpen(FARM_ID, "INDOOR_TEMP_HIGH");
    }

    @Test
    @DisplayName("P1-B: 멱등성 2차 방어선(partial unique index) 위반은 스케줄러 틱을 끊지 않고 흡수한다"
            + "(이슈 #116 리뷰 — recordBreach가 DataIntegrityViolationException을 던져도 evaluate는 "
            + "정상 완료하고 웹훅 발송까지 이어진다)")
    void dataIntegrityViolationOnRecordBreachIsSwallowed() {
        when(thresholdRepository.findEnabled())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(farm()));
        doThrow(new DataIntegrityViolationException("ux_alarm_events_open_farm_metric 위반(레이스 가정)"))
                .when(alarmEventService).recordBreach(eq(FARM_ID), any(), any(), eq("INDOOR_TEMP_HIGH"),
                        any(), any(), any());

        assertThatCode(() -> {
            service.evaluate(new Indoor(35.0, 50.0, true)); // 1틱
            service.evaluate(new Indoor(36.0, 50.0, true)); // 2틱 — recordBreach가 예외를 던짐
        }).doesNotThrowAnyException();

        // 알람 이벤트 저장은 실패했지만, 뒤이은 웹훅 발송(같은 evaluateDirection 호출)은 정상 진행돼야
        // 한다 — recordAlarmBreach의 catch가 evaluateDirection 이후 로직을 끊지 않는다는 뜻.
        verify(notifier, times(1)).notifyBreach(any(), eq(EnvMetric.INDOOR_TEMP), eq(EnvDirection.HIGH),
                eq(36.0), eq(30.0));
    }

    @Test
    @DisplayName("P2-A: 이미 정상이던 틱도 매번 자동 해소를 시도한다(인메모리 카운트만으로 열린 알람"
            + " 없음을 단정할 수 없음 — 이슈 #116 리뷰, 예전엔 여기서 조회를 생략했었다)")
    void alreadyNormalTickStillAttemptsAutoResolve() {
        when(thresholdRepository.findEnabled())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));

        service.evaluate(new Indoor(25.0, 50.0, true)); // 처음부터 정상
        service.evaluate(new Indoor(26.0, 50.0, true)); // 계속 정상

        // autoResolveIfOpen 자체가 열린 이벤트 없으면 no-op이라 매 정상 틱마다 호출해도 안전하다 —
        // 이 무조건 호출이 P2-A의 핵심 수정이다(resetFarm·앱 재시작으로 인메모리 카운트가 0으로
        // 리셋돼도 DB의 열린 이벤트를 놓치지 않기 위함).
        verify(alarmEventService, times(2)).autoResolveIfOpen(FARM_ID, "INDOOR_TEMP_HIGH");
    }

    @Test
    @DisplayName("P2-A: resetFarm으로 인메모리 연속 카운트가 초기화된 뒤에도 정상 틱이 오면 DB의 "
            + "열린 이벤트를 자동 해소한다(EnvThresholdService.updateThresholds가 설정 저장마다 "
            + "resetFarm을 호출하는데, 그 직후에도 유령 알람이 고착되지 않아야 함)")
    void autoResolveStillHappensAfterResetFarmClearsConsecutiveCount() {
        when(thresholdRepository.findEnabled())
                .thenReturn(List.of(thresholdEnabled(20.0, 30.0)));

        service.evaluate(new Indoor(35.0, 50.0, true)); // 1틱 이탈(DB에 열린 이벤트가 있다고 가정)
        service.resetFarm(FARM_ID); // 설정 저장 — 인메모리 연속 카운트가 0으로 리셋됨
        service.evaluate(new Indoor(25.0, 50.0, true)); // 리셋 후 첫 정상 틱

        // 리셋으로 previousConsecutive는 이미 0이었지만, 그와 무관하게 자동 해소를 시도해야 한다 —
        // 옛 로직(previousConsecutive>0일 때만 호출)이면 이 시나리오에서 영원히 호출되지 않는다.
        verify(alarmEventService, times(1)).autoResolveIfOpen(FARM_ID, "INDOOR_TEMP_HIGH");
    }

    @Test
    @DisplayName("회귀-A: 한 농장의 autoResolveIfOpen이 낙관적 락 충돌로 예외를 던져도 evaluate()는 "
            + "멈추지 않고 뒤 순서 농장 평가를 계속 진행한다(이슈 #116 리뷰 — evaluate() 루프 바디를 "
            + "농장 단위로 격리, recordAlarmBreach만 감싸던 개별 방어로는 autoResolveIfOpen 예외가 "
            + "여전히 for 루프를 끊었던 P1-B와 동일한 유실 경로)")
    void oneFarmAutoResolveFailureDoesNotBlockLaterFarms() {
        FarmEnvThreshold farm1Threshold = thresholdEnabled(20.0, 30.0); // FARM_ID — 정상 범위로 유지
        FarmEnvThreshold farm2Threshold = FarmEnvThreshold.builder()
                .farmId(FARM_ID_2)
                .enabled(true)
                .indoorTempMin(20.0)
                .indoorTempMax(24.0) // farm1보다 좁혀서 같은 indoor 값에 이탈하도록
                .build();
        when(thresholdRepository.findEnabled()).thenReturn(List.of(farm1Threshold, farm2Threshold));
        when(farmRepository.findById(FARM_ID_2)).thenReturn(Optional.of(farm()));
        // farm1은 정상 범위라 매 틱 autoResolveIfOpen이 호출되는데, 동시 acknowledge/resolve로 인한
        // 낙관적 락 충돌을 흉내내 예외를 던지게 한다.
        doThrow(new ObjectOptimisticLockingFailureException("AlarmEvent", FARM_ID))
                .when(alarmEventService).autoResolveIfOpen(eq(FARM_ID), any());

        assertThatCode(() -> {
            service.evaluate(new Indoor(25.0, 50.0, true)); // farm1 정상(예외 발생·흡수), farm2 1틱 이탈
            service.evaluate(new Indoor(25.0, 50.0, true)); // farm1 다시 예외, farm2 2틱 — 확정
        }).doesNotThrowAnyException();

        // farm1의 예외와 무관하게 farm2는 정상적으로 연속 2틱 이탈이 누적돼 알람 이벤트가 기록된다.
        verify(alarmEventService, times(1)).recordBreach(eq(FARM_ID_2), any(), any(),
                eq("INDOOR_TEMP_HIGH"), any(), any(), any());
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
