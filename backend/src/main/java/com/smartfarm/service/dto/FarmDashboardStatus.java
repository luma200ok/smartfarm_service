package com.smartfarm.service.dto;

/**
 * 홈 대시보드 카드 상태 배지(이슈 #139) — 농장의 <b>미확인</b> 알람 severity에서만 파생한다(다른
 * 신호를 섞지 않는다). CRITICAL 미확인 알람이 하나라도 있으면 CRITICAL, 없고 WARNING 미확인 알람이
 * 있으면 WARNING, 둘 다 없으면 NORMAL.
 */
public enum FarmDashboardStatus {
    NORMAL,
    WARNING,
    CRITICAL
}
