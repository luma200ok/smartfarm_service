package com.smartfarm.service.dto;

import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 장비 등록/수정 공용 요청(contract §4.10 — POST·PATCH 모두 {@code DeviceRequest}). PATCH는
 * "(부분)"이라 {@code name}·{@code kind} 같은 필수 필드도 여기서는 Bean Validation으로 강제하지
 * 않는다 — 생성 시 필수값 검증(공백/미지정 거부)과 위치 FK 3종 전부 null 거부(C001)는
 * {@code DeviceService#createDevice}가 담당한다(FarmUpdateRequest가 @NotNull 없이 null=미변경을
 * 표현하는 것과 동일 원칙). 수정 시 위치 FK 3종도 null=미변경이며 전체 해제는 1차 미지원.
 */
public record DeviceRequest(
        Long zoneId,
        Long rackId,
        Long rackLevelId,

        @Size(max = 50, message = "장비명은 50자 이하여야 합니다.")
        String name,

        DeviceKind kind,

        @Size(max = 50, message = "모델명은 50자 이하여야 합니다.")
        String model,

        @Size(max = 50, message = "시리얼은 50자 이하여야 합니다.")
        String serial,

        DeviceStatus status,

        LocalDateTime calibrationDueAt,

        LocalDate installedOn
) {

    public DeviceRequest {
        name = name == null ? null : name.trim();
        model = model == null ? null : model.trim();
        serial = serial == null ? null : serial.trim();
    }
}
