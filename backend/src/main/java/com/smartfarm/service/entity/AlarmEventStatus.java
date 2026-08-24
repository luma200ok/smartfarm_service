package com.smartfarm.service.entity;

/**
 * 알람 이벤트 상태(이슈 #116) — UNACKNOWLEDGED → ACKNOWLEDGED → RESOLVED 순으로만 전이한다.
 * RESOLVED는 종료 상태(사람 처리·시스템 자동 해소 모두 동일)로 재전이가 없다.
 */
public enum AlarmEventStatus {
    UNACKNOWLEDGED,
    ACKNOWLEDGED,
    RESOLVED;

    /** 미해결 여부 — 멱등성 판정(같은 farm×metricKey 조합의 열린 이벤트 존재 여부)에 쓰인다. */
    public boolean isOpen() {
        return this != RESOLVED;
    }
}
