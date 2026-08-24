package com.smartfarm.service.dto;

import com.smartfarm.service.entity.AlarmEventLog;
import com.smartfarm.service.entity.AlarmEventLogAction;
import java.time.LocalDateTime;

public record AlarmEventLogResponse(
        Long id,
        AlarmEventLogAction action,
        Long actorId,
        String note,
        LocalDateTime createdAt
) {

    public static AlarmEventLogResponse from(AlarmEventLog log) {
        return new AlarmEventLogResponse(
                log.getId(),
                log.getAction(),
                log.getActorId(),
                log.getNote(),
                log.getCreatedAt()
        );
    }
}
