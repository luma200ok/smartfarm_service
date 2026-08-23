package com.smartfarm.service.dto;

import com.smartfarm.service.entity.OperationMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 존 제어 상태 1회 조회(contract §4.12 — 모드 + 목표값 4종 + 장비 상태 + 대기 큐 + 최근 이력).
 * 제어 화면이 한 번의 요청으로 렌더된다.
 *
 * <p>{@code simulated}는 항상 true다 — 실기기가 없어 제어는 §4.11 가상 장비 시뮬레이터에만 작용한다
 * (contract §4.12 "시뮬레이션 전제"). 실제 기기를 제어하는 척하지 않기 위해 응답에 명시한다.
 */
public record ControlStateResponse(
        Long zoneId,
        String zoneName,
        OperationMode mode,
        Long modeUpdatedBy,
        LocalDateTime modeUpdatedAt,
        boolean simulated,
        List<ControlSetpointResponse> setpoints,
        List<ControlDeviceResponse> devices,
        List<ControlChangeResponse> pendingChanges,
        List<ControlApplyLogResponse> recentApplyLogs
) {
}
