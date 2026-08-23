package com.smartfarm.service.dto;

import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 장비 등록/수정 공용 요청(contract §4.10 — POST·PATCH 모두 {@code DeviceRequest}). PATCH는
 * "(부분)"이라 {@code name}·{@code kind} 같은 필수 필드도 여기서는 Bean Validation으로 강제하지
 * 않는다 — 생성 시 필수값 검증(공백/미지정 거부)과 위치 FK 3종 전부 null 거부(C001)는
 * {@code DeviceService#createDevice}가 담당한다(FarmUpdateRequest가 @NotNull 없이 null=미변경을
 * 표현하는 것과 동일 원칙). 수정 시 위치 FK 3종도 null=미변경이며 전체 해제는 1차 미지원.
 *
 * <p>name·model·serial엔 {@code ZoneUpdateRequest}/{@code RackUpdateRequest}와 동일한
 * {@code @Pattern}을 걸어둔다(리뷰 P3 #89) — trim 후 공백만 남은 값("   " → "")이 @Size만으로는
 * 통과해버려, PATCH로 이름을 빈 문자열로 만들거나(생성 시 C001 검사를 우회) serial=""가 NULL과
 * 달리 unique index 실값이 되어 두 번째 장비에서 409 E002가 나는 문제를 막는다.
 */
public record DeviceRequest(
        Long zoneId,
        Long rackId,
        Long rackLevelId,

        @Size(max = 50, message = "장비명은 50자 이하여야 합니다.")
        @Pattern(regexp = "^\\S(.*\\S)?$", message = "장비명은 공백으로 시작하거나 끝날 수 없습니다.")
        String name,

        DeviceKind kind,

        @Size(max = 50, message = "모델명은 50자 이하여야 합니다.")
        @Pattern(regexp = "^\\S(.*\\S)?$", message = "모델명은 공백으로 시작하거나 끝날 수 없습니다.")
        String model,

        @Size(max = 50, message = "시리얼은 50자 이하여야 합니다.")
        @Pattern(regexp = "^\\S(.*\\S)?$", message = "시리얼은 공백으로 시작하거나 끝날 수 없습니다.")
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
