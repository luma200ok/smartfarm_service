package com.smartfarm.service.dto;

import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmEventStatus;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import java.time.LocalDateTime;

public record AlarmEventResponse(
        Long id,
        Long farmId,
        AlarmSeverity severity,
        AlarmSourceType sourceType,
        String metricKey,
        String message,
        AlarmEventStatus status,
        LocalDateTime occurredAt,
        LocalDateTime acknowledgedAt,
        Long acknowledgedBy,
        LocalDateTime resolvedAt,
        Long resolvedBy,
        Long thresholdId,
        LocalDateTime createdAt
) {

    public static AlarmEventResponse from(AlarmEvent event) {
        return new AlarmEventResponse(
                event.getId(),
                event.getFarmId(),
                event.getSeverity(),
                event.getSourceType(),
                event.getMetricKey(),
                event.getMessage(),
                event.getStatus(),
                event.getOccurredAt(),
                event.getAcknowledgedAt(),
                event.getAcknowledgedBy(),
                event.getResolvedAt(),
                event.getResolvedBy(),
                event.getThresholdId(),
                event.getCreatedAt()
        );
    }
}
