package com.smartfarm.service.service;

import com.smartfarm.service.dto.AiEnvironmentResponse;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.entity.FarmEnvThreshold;
import com.smartfarm.service.repository.FarmEnvThresholdRepository;
import com.smartfarm.service.repository.FarmRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 환경 임계치 평가·알림(contract §4.6, 이슈 #52) — {@code EnvironmentSnapshotPoller}가 새 스냅샷을
 * 적재한 직후(중복 skip이 아닐 때만) 호출한다. 연속 2틱 이탈 시 발동, 농장×항목×방향별 쿨다운
 * 30분. 상태는 메모리(단일 인스턴스 전제, 재시작 시 초기화 수용 — contract 명시).
 *
 * <p>단일 스케줄러 스레드가 순차 호출하므로 별도 락은 필요 없다({@link ConcurrentHashMap}은
 * 안전망일 뿐 실질적 동시 접근은 없다).
 */
@Component
public class EnvThresholdAlertService {

    static final int CONSECUTIVE_THRESHOLD = 2;
    static final Duration COOLDOWN = Duration.ofMinutes(30);

    private final FarmEnvThresholdRepository farmEnvThresholdRepository;
    private final FarmRepository farmRepository;
    private final EnvThresholdWebhookNotifier notifier;
    private final Clock clock;

    private final ConcurrentHashMap<AlertKey, AlertState> states = new ConcurrentHashMap<>();

    @Autowired
    public EnvThresholdAlertService(FarmEnvThresholdRepository farmEnvThresholdRepository,
                                     FarmRepository farmRepository,
                                     EnvThresholdWebhookNotifier notifier) {
        this(farmEnvThresholdRepository, farmRepository, notifier, Clock.systemDefaultZone());
    }

    /** 테스트 전용 — 30분 쿨다운 만료를 실시간 대기 없이 검증하기 위해 Clock을 주입한다(package-private). */
    EnvThresholdAlertService(FarmEnvThresholdRepository farmEnvThresholdRepository,
                              FarmRepository farmRepository,
                              EnvThresholdWebhookNotifier notifier,
                              Clock clock) {
        this.farmEnvThresholdRepository = farmEnvThresholdRepository;
        this.farmRepository = farmRepository;
        this.notifier = notifier;
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
            state.consecutiveBreaches.set(0);
            return;
        }
        int consecutive = state.consecutiveBreaches.incrementAndGet();
        if (consecutive < CONSECUTIVE_THRESHOLD) {
            return;
        }

        Instant now = Instant.now(clock);
        Instant lastNotifiedAt = state.lastNotifiedAt.get();
        if (lastNotifiedAt != null && Duration.between(lastNotifiedAt, now).compareTo(COOLDOWN) < 0) {
            return; // 쿨다운 중
        }
        state.lastNotifiedAt.set(now);

        farmRepository.findById(threshold.getFarmId())
                .ifPresent(farm -> notifier.notifyBreach(farm, metric, direction, value, boundary));
    }

    /** 테스트 격리용 — 싱글턴 빈이라 통합 테스트 간 상태가 새지 않도록 초기화한다. */
    public void resetState() {
        states.clear();
    }

    private record AlertKey(Long farmId, EnvMetric metric, EnvDirection direction) {
    }

    private static final class AlertState {
        private final AtomicInteger consecutiveBreaches = new AtomicInteger(0);
        private final AtomicReference<Instant> lastNotifiedAt = new AtomicReference<>();
    }
}
