package com.smartfarm.service.dto;

import com.smartfarm.service.entity.SystemLog;
import com.smartfarm.service.entity.SystemLogCategory;
import java.time.LocalDateTime;

/** 시스템 로그 응답(V24, 이슈 #129-A) — append-only라 수정 이력 필드가 없다. */
public record SystemLogResponse(
        Long id,
        SystemLogCategory category,
        String message,
        Long actorId,
        LocalDateTime occurredAt
) {

    public static SystemLogResponse from(SystemLog log) {
        return new SystemLogResponse(log.getId(), log.getCategory(), log.getMessage(), log.getActorId(),
                log.getOccurredAt());
    }
}
