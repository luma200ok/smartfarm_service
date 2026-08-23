package com.smartfarm.service.service;

import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;

/**
 * 백엔드 전역 챗 동시성 가드(보안 리뷰 P1-A, 2026-08-23) — ai-server의 챗 하위 슬롯(상한 1,
 * contract §4.7 "챗 전용 하위 상한 1")에 대응해 backend도 동시 처리 챗 요청 수를 상한선으로 묶는다.
 *
 * <p><b>공격 경로</b>: 농장 단위(+사용자 단위) 레이트리밋은 "한 주체가 만드는 요청 빈도"만 제한할
 * 뿐, "여러 계정이 여러 농장을 만들어 각자 상한까지 동시에 때리면" backend 스레드가 챗 응답 대기
 * (최대 120s)로 계속 쌓여 Tomcat 스레드 풀이 고갈될 수 있다(nginx {@code limit_req}는 auth 4개
 * 엔드포인트에만 적용되고 {@code /api/} catch-all은 무방비). 이 가드는 농장·사용자 수와 무관하게
 * "backend가 동시에 붙잡는 챗 스레드"를 절대 상한으로 묶어 이 경로를 차단한다.
 *
 * <p>{@link Semaphore#tryAcquire()}(non-blocking)만 쓴다 — 대기(blocking acquire)하면 요청
 * 스레드가 슬롯을 기다리며 그대로 점유되어 방어 의미가 없다(오히려 더 많은 스레드가 대기 상태로
 * 쌓여 풀 고갈을 앞당긴다). 포화 시 즉시 거절(CH002)하는 것이 핵심.
 */
@Component
public class ChatConcurrencyGuard {

    /** backend가 동시에 처리하는 챗 요청 상한. ai-server 챗 슬롯(1)보다 약간 여유를 둬 백오프·
     * 재시도 여지를 남기되, 무제한 누적은 차단한다. */
    static final int MAX_CONCURRENT_CHATS = 2;

    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT_CHATS);

    /** true = 슬롯 확보(호출자가 반드시 {@link #release()}를 try-finally로 호출해야 한다), false = 포화. */
    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    public void release() {
        semaphore.release();
    }

    /** 테스트 전용 — 잔여 슬롯 수 확인(package-private). */
    int availablePermits() {
        return semaphore.availablePermits();
    }

    /** 테스트 격리용 — 싱글턴 빈이라 통합 테스트 간 상태가 새지 않도록 슬롯을 전부 회복시킨다. */
    public void resetState() {
        semaphore.drainPermits();
        semaphore.release(MAX_CONCURRENT_CHATS);
    }
}
