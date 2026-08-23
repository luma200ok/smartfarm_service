package com.smartfarm.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 랙 생성 요청(contract §4.10) — levelCount만큼 층이 자동 생성된다. */
public record RackRequest(
        @NotBlank(message = "랙 코드는 필수입니다.")
        @Size(max = 30, message = "랙 코드는 30자 이하여야 합니다.")
        String code,

        @NotNull(message = "층수는 필수입니다.")
        @Min(value = 1, message = "층수는 1 이상이어야 합니다.")
        @Max(value = 50, message = "층수는 50 이하여야 합니다.")
        Integer levelCount,

        Integer displayOrder
) {

    public RackRequest {
        code = code == null ? null : code.trim();
    }
}
