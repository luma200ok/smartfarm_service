package com.smartfarm.service.dto;

import com.smartfarm.service.entity.ControlChange;
import com.smartfarm.service.entity.ControlChangeKind;
import com.smartfarm.service.entity.ControlChangeStatus;
import com.smartfarm.service.entity.SensorMetric;
import java.time.LocalDateTime;
import java.util.List;

/** 적용 대기 큐 항목(contract §4.12). */
public record ControlChangeResponse(
        Long id,
        ControlChangeKind kind,
        SensorMetric metric,
        String unit,
        Long deviceId,
        String fromValue,
        String toValue,
        ControlChangeStatus status,
        Long createdBy,
        LocalDateTime createdAt,
        Long appliedBy,
        LocalDateTime appliedAt
) {

    public static ControlChangeResponse from(ControlChange change) {
        return new ControlChangeResponse(
                change.getId(),
                change.getKind(),
                change.getMetric(),
                change.getMetric() != null ? change.getMetric().unit() : null,
                change.getDeviceId(),
                change.getFromValue(),
                change.getToValue(),
                change.getStatus(),
                change.getCreatedBy(),
                change.getCreatedAt(),
                change.getAppliedBy(),
                change.getAppliedAt());
    }

    public static List<ControlChangeResponse> from(List<ControlChange> changes) {
        return changes.stream().map(ControlChangeResponse::from).toList();
    }
}
