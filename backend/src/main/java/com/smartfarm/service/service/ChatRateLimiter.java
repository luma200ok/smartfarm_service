package com.smartfarm.service.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 챗 레이트리밋(handoff #54) — 처방 접수 상한(P004)이 DB count 기반인 것과 달리, 챗은 동기 응답이라
 * 별도 카운터가 필요하다. <b>농장 단위</b> 분당 상한을 둔다(caller_ref가 {@code svc:farm:{farmId}}로
 * 농장 단위로 ai-server에 전달되는 것과 동일한 스코프 — contract §4.7 "농장·사용자 단위 레이트리밋"
 * 중 농장 단위를 선택: 데모 계정을 포함해 같은 농장을 쓰는 여러 사용자의 합산 남용을 막는 것이 목적).
 *
 * <p>상태는 메모리(단일 인스턴스 전제, 재시작 시 초기화 수용 — {@link EnvThresholdAlertService}와
 * 동일 트레이드오프). 고정 윈도(분 단위 truncate) 방식이라 윈도 경계에서 순간적으로 상한의 최대 2배
 * 가까이 허용될 수 있으나(예: 0:59에 10건 + 1:00에 10건), 목적이 폭주성 남용 완화이지 엄밀한
 * 토큰버킷이 아니므로 이 근사를 수용한다(P004의 count-저장 TOCTOU 수용과 같은 결).
 */
@Component
public class ChatRateLimiter {

    /** 농장당 분당 허용 요청 수 — ai-server 챗 하위 상한(1)을 감안해 짧은 폭주만 걸러내는 보수적 값. */
    static final int LIMIT_PER_MINUTE = 10;

    private final Clock clock;
    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public ChatRateLimiter() {
        this(Clock.systemDefaultZone());
    }

    /** 테스트 전용 — 분 경계 전이를 실시간 대기 없이 검증하기 위해 Clock을 주입한다(package-private). */
    ChatRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** true = 허용(카운트 소비), false = 초과(호출자가 CH002로 매핑). */
    public boolean tryAcquire(Long farmId) {
        Window window = windows.computeIfAbsent(farmId, key -> new Window());
        synchronized (window) {
            Instant currentMinute = Instant.now(clock).truncatedTo(ChronoUnit.MINUTES);
            if (!currentMinute.equals(window.minuteStart)) {
                window.minuteStart = currentMinute;
                window.count = 0;
            }
            if (window.count >= LIMIT_PER_MINUTE) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    /** 테스트 격리용 — 싱글턴 빈이라 통합 테스트 간 상태가 새지 않도록 초기화한다. */
    public void resetState() {
        windows.clear();
    }

    private static final class Window {
        private Instant minuteStart;
        private int count;
    }
}
