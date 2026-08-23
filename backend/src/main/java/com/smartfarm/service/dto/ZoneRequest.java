package com.smartfarm.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 존 생성 요청(contract §4.10) — displayOrder 미지정 시 0(insertion 순서는 id로 안정 정렬). */
public record ZoneRequest(
        @NotBlank(message = "존 이름은 필수입니다.")
        @Size(max = 50, message = "존 이름은 50자 이하여야 합니다.")
        String name,

        Integer displayOrder
) {

    public ZoneRequest {
        // trim 후 검증(@NotBlank/@Size) — 공백 패딩으로 길이 제한 우회 차단 (FarmRequest 선례)
        name = name == null ? null : name.trim();
    }
}
