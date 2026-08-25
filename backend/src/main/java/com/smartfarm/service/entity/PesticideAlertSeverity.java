package com.smartfarm.service.entity;

/**
 * 병해충 발생주의 경보 등급(V23, 이슈 #128) — 프리뷰 mock {@code PESTICIDE_ALERTS}의
 * {@code tone: "warning"|"plain"}을 구조화한 것. WARNING=경보(방제 조치 권장),
 * INFO=일반 안내(참고용, 즉각 조치 불필요).
 */
public enum PesticideAlertSeverity {
    WARNING,
    INFO
}
