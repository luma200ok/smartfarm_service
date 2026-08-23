package com.smartfarm.service.entity;

/**
 * 적용 대기 큐 항목 상태(contract §4.12) — 큐 = {@code PENDING} 목록. {@code APPLIED}·
 * {@code DISCARDED}는 <b>종료 상태</b>로 재전이가 금지된다({@link PrescriptionStatus#isTerminal()}
 * 선례 — contract §4.12 동시성 5).
 */
public enum ControlChangeStatus {

    PENDING,
    APPLIED,
    DISCARDED;

    /** 종료 상태 여부 — 종료 상태에서는 어떤 전이도 허용하지 않는다. */
    public boolean isTerminal() {
        return this == APPLIED || this == DISCARDED;
    }
}
