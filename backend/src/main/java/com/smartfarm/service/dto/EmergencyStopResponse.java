package com.smartfarm.service.dto;

import java.time.LocalDateTime;

/**
 * 비상 정지 결과(contract §4.12 — POST /control/emergency-stop, 농장 전체). 전 존의
 * <b>제어기(kind=CONTROLLER)</b>를 OFF로 내리고 모드를 MANUAL로 바꾼 뒤 <b>모든 PENDING 큐를 폐기</b>한
 * 결과를 집계해 돌려준다.
 *
 * <p>{@code stoppedDeviceCount}는 이번 호출로 실제 상태가 바뀐 <b>제어기</b> 수다 — 센서·게이트웨이는
 * 애초에 대상이 아니고(정지 중에도 관측은 계속된다), 이미 OFF이거나 통신 두절(OFFLINE)인 제어기도
 * 제외되므로 포함되지 않는다.
 */
public record EmergencyStopResponse(
        Long farmId,
        int zoneCount,
        int stoppedDeviceCount,
        int discardedChangeCount,
        LocalDateTime stoppedAt,
        boolean simulated
) {
}
