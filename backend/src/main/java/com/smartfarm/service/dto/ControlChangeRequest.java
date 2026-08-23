package com.smartfarm.service.dto;

import com.smartfarm.service.entity.ControlChangeKind;
import com.smartfarm.service.entity.DeviceStatus;
import com.smartfarm.service.entity.SensorMetric;
import jakarta.validation.constraints.NotNull;

/**
 * 적용 대기 큐 적재 요청(contract §4.12 — POST /control/changes). 큐에 쌓기만 하고
 * <b>장비에 즉시 반영하지 않는다</b>(반영은 apply).
 *
 * <p>종류별 필수 필드가 다르다 — {@code SETPOINT}는 {@code metric}·{@code targetValue},
 * {@code DEVICE}는 {@code deviceId}·{@code targetStatus}. 조합 검증은 Bean Validation으로
 * 표현할 수 없어(교차 필드) {@code ControlService}가 C001로 처리한다(DeviceRequest 선례).
 *
 * <p>{@code targetStatus}는 켜기({@code NORMAL})/끄기({@code OFF})만 허용한다 — 나머지 상태는
 * 관측 결과이지 조작 대상이 아니다(위반 시 C001).
 */
public record ControlChangeRequest(

        @NotNull(message = "변경 종류는 필수입니다.")
        ControlChangeKind kind,

        SensorMetric metric,

        Double targetValue,

        Long deviceId,

        DeviceStatus targetStatus
) {
}
