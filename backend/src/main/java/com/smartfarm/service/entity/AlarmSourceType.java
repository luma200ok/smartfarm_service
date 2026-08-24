package com.smartfarm.service.entity;

/**
 * 알람 발생 소스(이슈 #116) — 현재는 환경 임계치 1종뿐이지만, 향후 장비 통신 두절·재고 부족 등
 * 다른 소스로 확장할 것을 대비해 enum으로 둔다(handoff).
 */
public enum AlarmSourceType {
    ENV_THRESHOLD
}
