package com.smartfarm.service.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.service.entity.Schedule;
import com.smartfarm.service.entity.ScheduleActionType;
import java.time.LocalDateTime;

/** 스케줄 응답(이슈 #129-C) — {@code actionPayload}는 저장된 JSON을 파싱한 값(서비스가 채워 넣는다). */
public record ScheduleResponse(
        Long id,
        Long farmId,
        Long zoneId,
        String name,
        boolean enabled,
        String cronExpression,
        ScheduleActionType actionType,
        JsonNode actionPayload,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ScheduleResponse from(Schedule schedule, JsonNode actionPayload) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getFarmId(),
                schedule.getZoneId(),
                schedule.getName(),
                schedule.isEnabled(),
                schedule.getCronExpression(),
                schedule.getActionType(),
                actionPayload,
                schedule.getCreatedBy(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt()
        );
    }
}
