package com.smartfarm.service.dto;

import com.smartfarm.service.entity.CropType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FarmRequest(
        @NotBlank(message = "농장 이름은 필수입니다.")
        @Size(min = 2, max = 50, message = "농장 이름은 2~50자여야 합니다.")
        String name,

        @NotNull(message = "작물 종류는 필수입니다.")
        CropType cropType,

        @Size(max = 255, message = "위치는 255자 이하여야 합니다.")
        String location
) {

    public FarmRequest {
        // trim 후 검증(@NotBlank/@Size) — 공백 패딩으로 길이 제한 우회 차단 (EmailNormalizer 선례)
        name = name == null ? null : name.trim();
    }
}
