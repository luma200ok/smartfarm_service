package com.smartfarm.service.entity;

/**
 * 처방 비동기 job 상태(contract §3): PENDING → PROCESSING → COMPLETED | FAILED.
 * JSON 직렬화는 계약 표기 그대로 상수명(대문자)을 쓴다(진단 status의 소문자 계약과 다름 — 계약 §3 명시 표기).
 */
public enum PrescriptionStatus {

    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    /** 종료 상태 여부 — 종료 상태에서는 어떤 전이도 허용하지 않는다. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
