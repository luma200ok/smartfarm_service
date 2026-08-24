package com.smartfarm.service.dto;

import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmEventStatus;
import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import java.time.LocalDateTime;

/**
 * 알람 이벤트 응답(contract §4.13). {@code ruleId}/{@code scopeType}/{@code scopeId}는 이슈 #118에서
 * 추가됐다 — 프리뷰 알람 화면의 위치 표기("군산1 · B3랙 4층")를 프런트가 조립하려면 이벤트 자체에
 * 스코프가 실려 있어야 한다. #118 이전에 생성된 과거 이벤트는 세 값 모두 null(=농장 단위)이다.
 */
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
        Long ruleId,
        AlarmScopeType scopeType,
        Long scopeId,
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
                event.getRuleId(),
                event.getScopeType(),
                event.getScopeId(),
                event.getCreatedAt()
        );
    }
}
