package com.smartfarm.service.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH 부분 수정 요청 — null 필드는 미변경(FarmUpdateRequest와 동일 패턴, contract §4.10). */
public record ZoneUpdateRequest(
        @Size(max = 50, message = "존 이름은 50자 이하여야 합니다.")
        @Pattern(regexp = "^\\S(.*\\S)?$", message = "존 이름은 공백으로 시작하거나 끝날 수 없습니다.")
        String name,

        Integer displayOrder
) {

    public ZoneUpdateRequest {
        // trim 후 검증 — 공백만 온 이름("   ")은 ""가 되어 @Size/@Pattern에서 C001로 거부
        name = name == null ? null : name.trim();
    }
}
