package com.smartfarm.service.dto;

import com.smartfarm.service.entity.ControlSetpoint;
import com.smartfarm.service.entity.SensorMetric;
import java.time.LocalDateTime;

/**
 * 존×지표 목표값(contract §4.12) — 제어 가능한 4종을 <b>항상 전부</b> 싣고, 미설정 지표는
 * {@code targetValue}가 null이다(프리뷰 목표값 카드가 4칸 고정이라 목록 길이가 흔들리면 안 된다).
 */
public record ControlSetpointResponse(
        SensorMetric metric,
        String unit,
        Double targetValue,
        Long updatedBy,
        LocalDateTime updatedAt
) {

    /** 미설정 지표 — 값 없이 지표·단위만. */
    public static ControlSetpointResponse unset(SensorMetric metric) {
        return new ControlSetpointResponse(metric, metric.unit(), null, null, null);
    }

    public static ControlSetpointResponse from(ControlSetpoint setpoint) {
        return new ControlSetpointResponse(
                setpoint.getMetric(),
                setpoint.getMetric().unit(),
                setpoint.getTargetValue(),
                setpoint.getUpdatedBy(),
                setpoint.getUpdatedAt() != null ? setpoint.getUpdatedAt() : setpoint.getCreatedAt());
    }
}
