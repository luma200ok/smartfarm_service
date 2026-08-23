package com.smartfarm.service.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 챗 레이트리밋(handoff #54 + 보안 리뷰 P1-B, 2026-08-23) — 처방 접수 상한(P004)이 DB count
 * 기반인 것과 달리, 챗은 동기 응답이라 별도 카운터가 필요하다. <b>농장 단위 + 사용자 단위</b> 분당
 * 상한을 모두 적용한다(contract §4.7 갱신 예정 — 메타 확정: 농장 단위만으로는 한 계정이 농장을
 * 계속 늘려 우회할 수 있어 사용자 단위를 함께 둔다).
 *
 * <p>{@link #tryAcquire(Long, Long)}은 농장 슬롯을 먼저 소비하고, 사용자 슬롯 확인에 실패하면
 * 농장 슬롯을 롤백한다 — 최종적으로 거부되는 요청이 농장 쿼터를 낭비하지 않게 하는 근사적 원자성이다
 * (완전한 분산 트랜잭션은 아니지만, 단일 인스턴스·근사 레이트리밋이라는 목적에는 충분하다).
 *
 * <p>상태는 메모리(단일 인스턴스 전제, 재시작 시 초기화 수용 — {@link EnvThresholdAlertService}와
 * 동일 트레이드오프). 고정 윈도(분 단위 truncate) 방식이라 윈도 경계에서 순간적으로 상한의 최대 2배
 * 가까이 허용될 수 있으나(예: 0:59에 10건 + 1:00에 10건), 목적이 폭주성 남용 완화이지 엄밀한
 * 토큰버킷이 아니므로 이 근사를 수용한다(P004의 count-저장 TOCTOU 수용과 같은 결).
 *
 * <p>맵 무한 증가 방지(보안 리뷰 P2): 각 맵의 크기가 {@link #cleanupThreshold}를 넘으면 다음
 * {@code tryAcquire} 호출 시점에 현재 분(minute)이 아닌(=만료된) 엔트리를 청소한다. 활성 사용자가
 * 지속적으로 요청하는 동안은 그 엔트리가 매번 최신 분으로 갱신돼 청소 대상이 아니고, 더는 요청하지
 * 않는 farmId·userId만 정리 대상이 된다.
 */
@Component
public class ChatRateLimiter {

    /** 농장당/사용자당 분당 허용 요청 수 — ai-server 챗 하위 상한(1)을 감안해 짧은 폭주만 걸러내는 보수적 값. */
    static final int LIMIT_PER_MINUTE = 10;

    /** 맵 크기가 이 값 이상이면 tryAcquire 시점에 만료 엔트리 청소를 시도한다. */
    private static final int DEFAULT_CLEANUP_THRESHOLD = 500;

    private final Clock clock;
    private final int cleanupThreshold;
    private final ConcurrentHashMap<Long, Window> farmWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Window> userWindows = new ConcurrentHashMap<>();

    @Autowired
    public ChatRateLimiter() {
        this(Clock.systemDefaultZone());
    }

    /** 테스트 전용 — 분 경계 전이를 실시간 대기 없이 검증하기 위해 Clock을 주입한다(package-private). */
    ChatRateLimiter(Clock clock) {
        this(clock, DEFAULT_CLEANUP_THRESHOLD);
    }

    /** 테스트 전용 — 청소 임계값을 작게 둬 무한 증가 방지 로직을 소수 엔트리로 검증한다(package-private). */
    ChatRateLimiter(Clock clock, int cleanupThreshold) {
        this.clock = clock;
        this.cleanupThreshold = cleanupThreshold;
    }

    /** true = 허용(농장·사용자 카운트 모두 소비), false = 둘 중 하나라도 초과(호출자가 CH002로 매핑). */
    public boolean tryAcquire(Long farmId, Long userId) {
        Instant farmMinute = tryAcquireWindow(farmWindows, farmId);
        if (farmMinute == null) {
            return false;
        }
        Instant userMinute = tryAcquireWindow(userWindows, userId);
        if (userMinute == null) {
            releaseWindow(farmWindows, farmId, farmMinute);
            return false;
        }
        return true;
    }

    /** 성공 시 소비된 윈도의 분 경계(롤백용)를, 실패(상한 초과) 시 null을 반환한다. */
    private Instant tryAcquireWindow(ConcurrentHashMap<Long, Window> windows, Long key) {
        cleanupExpiredIfNeeded(windows);
        Window window = windows.computeIfAbsent(key, k -> new Window());
        synchronized (window) {
            Instant currentMinute = Instant.now(clock).truncatedTo(ChronoUnit.MINUTES);
            if (!currentMinute.equals(window.minuteStart)) {
                window.minuteStart = currentMinute;
                window.count = 0;
            }
            if (window.count >= LIMIT_PER_MINUTE) {
                return null;
            }
            window.count++;
            return currentMinute;
        }
    }

    /** acquiredMinute가 여전히 현재 윈도일 때만 되돌린다 — 그 사이 분이 넘어갔다면 새 윈도를 잘못 건드리지 않는다. */
    private void releaseWindow(ConcurrentHashMap<Long, Window> windows, Long key, Instant acquiredMinute) {
        Window window = windows.get(key);
        if (window == null) {
            return;
        }
        synchronized (window) {
            if (acquiredMinute.equals(window.minuteStart) && window.count > 0) {
                window.count--;
            }
        }
    }

    private void cleanupExpiredIfNeeded(ConcurrentHashMap<Long, Window> windows) {
        if (windows.size() < cleanupThreshold) {
            return;
        }
        Instant currentMinute = Instant.now(clock).truncatedTo(ChronoUnit.MINUTES);
        windows.entrySet().removeIf(entry -> {
            Window window = entry.getValue();
            synchronized (window) {
                return !currentMinute.equals(window.minuteStart);
            }
        });
    }

    /** 테스트 격리용 — 싱글턴 빈이라 통합 테스트 간 상태가 새지 않도록 초기화한다. */
    public void resetState() {
        farmWindows.clear();
        userWindows.clear();
    }

    /** 테스트 전용 — 맵 무한 증가 방지(청소) 검증용 크기 조회(package-private). */
    int farmWindowCount() {
        return farmWindows.size();
    }

    /** 테스트 전용 — 맵 무한 증가 방지(청소) 검증용 크기 조회(package-private). */
    int userWindowCount() {
        return userWindows.size();
    }

    private static final class Window {
        private Instant minuteStart;
        private int count;
    }
}
