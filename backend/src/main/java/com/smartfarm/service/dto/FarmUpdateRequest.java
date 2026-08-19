package com.smartfarm.service.dto;

import com.smartfarm.service.entity.CropType;
import jakarta.validation.constraints.Size;

/**
 * PATCH 부분 수정 요청 — null 필드는 미변경 (contract §3: FarmRequest 부분).
 */
public record FarmUpdateRequest(
        @Size(min = 2, max = 50, message = "농장 이름은 2~50자여야 합니다.")
        String name,

        CropType cropType,

        @Size(max = 255, message = "위치는 255자 이하여야 합니다.")
        String location
) {
}
