package com.smartfarm.service.entity;

/**
 * 시스템 로그 카테고리(V24, 이슈 #129-A) — 기록 지점 4곳에 1:1로 대응한다:
 * <ul>
 *   <li>{@link #CONTROL} — 존 운전 모드 변경({@code ControlService#changeMode}).</li>
 *   <li>{@link #MEMBER} — 초대코드 발급({@code InvitationService#createInvitation}).</li>
 *   <li>{@link #ALARM} — 알람 이벤트 생성({@code AlarmEventService#recordBreach}, actorId null).</li>
 *   <li>{@link #DEVICE} — 장비 등록/수정({@code DeviceService#createDevice}/{@code #updateDevice}).</li>
 * </ul>
 *
 * <p>⚠️ 위 4곳 <b>외에는 기록 지점을 추가하지 않는다</b>(handoff — 모든 서비스에 로깅을 뿌리면 범위가
 * 폭발한다). 펌웨어 배포·리포트 생성처럼 아직 없는 기능의 카테고리는 만들지 않는다 — 값은 있는데
 * 아무도 기록하지 않는 죽은 카테고리는 "이 필터를 걸면 왜 항상 비는가"라는 혼란만 남긴다.
 */
public enum SystemLogCategory {
    CONTROL,
    MEMBER,
    ALARM,
    DEVICE
}
