package com.smartfarm.service.dto;

import com.smartfarm.service.entity.FarmLog;
import com.smartfarm.service.entity.FarmLogType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FarmLogResponse(
        Long id,
        LocalDate logDate,
        FarmLogType type,
        String memo,
        Long createdBy,
        LocalDateTime createdAt
) {

    public static FarmLogResponse from(FarmLog log) {
        return new FarmLogResponse(
                log.getId(),
                log.getLogDate(),
                log.getType(),
                log.getMemo(),
                log.getAuthor(),
                log.getCreatedAt()
        );
    }
}
