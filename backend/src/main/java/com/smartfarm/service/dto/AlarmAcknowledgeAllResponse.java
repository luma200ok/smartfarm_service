package com.smartfarm.service.dto;

/** 전체 확인 처리 결과 — 몇 건이 UNACKNOWLEDGED → ACKNOWLEDGED로 전이됐는지(이슈 #116). */
public record AlarmAcknowledgeAllResponse(int acknowledgedCount) {
}
