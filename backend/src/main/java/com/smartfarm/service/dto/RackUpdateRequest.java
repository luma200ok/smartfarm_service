package com.smartfarm.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH 부분 수정 요청 — null 필드는 미변경(contract §4.10). levelCount 축소는 서비스가 R004 검증. */
public record RackUpdateRequest(
        @Size(max = 30, message = "랙 코드는 30자 이하여야 합니다.")
        @Pattern(regexp = "^\\S(.*\\S)?$", message = "랙 코드는 공백으로 시작하거나 끝날 수 없습니다.")
        String code,

        @Min(value = 1, message = "층수는 1 이상이어야 합니다.")
        @Max(value = 50, message = "층수는 50 이하여야 합니다.")
        Integer levelCount,

        Integer displayOrder
) {

    public RackUpdateRequest {
        code = code == null ? null : code.trim();
    }
}
