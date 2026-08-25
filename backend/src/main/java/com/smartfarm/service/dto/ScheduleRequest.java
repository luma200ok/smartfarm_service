package com.smartfarm.service.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.service.entity.ScheduleActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 스케줄 생성 요청(이슈 #129-C) — <b>저장만</b> 한다(실행 경로 없음, {@code Schedule} 클래스 주석 참고).
 *
 * @param zoneId         존 단위로 좁힐 때만 지정(null이면 농장 전체 대상). 지정 시 그 농장 소속인지
 *                       재확인한다(cross-tenant IDOR 차단, 미소속은 404 R001).
 * @param cronExpression Spring {@code CronExpression.parse}로 형식을 검증한다(400 SCH003).
 * @param actionPayload  actionType별로 형태가 다른 임의 JSON(골격 단계라 스키마를 강제하지 않음).
 */
public record ScheduleRequest(
        Long zoneId,
        @NotBlank @Size(max = 50) String name,
        Boolean enabled,
        @NotBlank @Size(max = 100) String cronExpression,
        @NotNull ScheduleActionType actionType,
        JsonNode actionPayload
) {
}
