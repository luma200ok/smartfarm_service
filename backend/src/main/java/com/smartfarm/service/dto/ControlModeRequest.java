package com.smartfarm.service.dto;

import com.smartfarm.service.entity.OperationMode;
import jakarta.validation.constraints.NotNull;

/** 운전 모드 변경 요청(contract §4.12 — PUT /control/mode). */
public record ControlModeRequest(

        @NotNull(message = "운전 모드는 필수입니다.")
        OperationMode mode
) {
}
