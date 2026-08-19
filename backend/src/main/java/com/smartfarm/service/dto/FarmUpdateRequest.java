package com.smartfarm.service.dto;

import com.smartfarm.service.entity.CropType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH 부분 수정 요청 — null 필드는 미변경, location 비우기는 1차 미지원 (contract §3).
 */
public record FarmUpdateRequest(
        @Size(min = 2, max = 50, message = "농장 이름은 2~50자여야 합니다.")
        @Pattern(regexp = "^\\S(.*\\S)?$", message = "농장 이름은 공백으로 시작하거나 끝날 수 없습니다.")
        String name,

        CropType cropType,

        @Size(max = 255, message = "위치는 255자 이하여야 합니다.")
        String location
) {

    public FarmUpdateRequest {
        // trim 후 검증 — 공백만 온 이름("   ")은 ""가 되어 @Size/@Pattern에서 C001로 거부
        name = name == null ? null : name.trim();
    }
}
