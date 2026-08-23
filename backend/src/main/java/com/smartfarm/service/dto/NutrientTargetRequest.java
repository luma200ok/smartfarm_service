package com.smartfarm.service.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** 목표 ppm(mg/L) 6성분(contract §4.9) — 각 0~1000. */
public record NutrientTargetRequest(
        @NotNull(message = "질소(N) 목표치는 필수입니다.")
        @DecimalMin(value = "0", message = "질소(N) 목표치는 0 이상이어야 합니다.")
        @DecimalMax(value = "1000", message = "질소(N) 목표치는 1000 이하여야 합니다.")
        Double n,

        @NotNull(message = "인(P) 목표치는 필수입니다.")
        @DecimalMin(value = "0", message = "인(P) 목표치는 0 이상이어야 합니다.")
        @DecimalMax(value = "1000", message = "인(P) 목표치는 1000 이하여야 합니다.")
        Double p,

        @NotNull(message = "칼륨(K) 목표치는 필수입니다.")
        @DecimalMin(value = "0", message = "칼륨(K) 목표치는 0 이상이어야 합니다.")
        @DecimalMax(value = "1000", message = "칼륨(K) 목표치는 1000 이하여야 합니다.")
        Double k,

        @NotNull(message = "칼슘(Ca) 목표치는 필수입니다.")
        @DecimalMin(value = "0", message = "칼슘(Ca) 목표치는 0 이상이어야 합니다.")
        @DecimalMax(value = "1000", message = "칼슘(Ca) 목표치는 1000 이하여야 합니다.")
        Double ca,

        @NotNull(message = "마그네슘(Mg) 목표치는 필수입니다.")
        @DecimalMin(value = "0", message = "마그네슘(Mg) 목표치는 0 이상이어야 합니다.")
        @DecimalMax(value = "1000", message = "마그네슘(Mg) 목표치는 1000 이하여야 합니다.")
        Double mg,

        @NotNull(message = "황(S) 목표치는 필수입니다.")
        @DecimalMin(value = "0", message = "황(S) 목표치는 0 이상이어야 합니다.")
        @DecimalMax(value = "1000", message = "황(S) 목표치는 1000 이하여야 합니다.")
        Double s
) {
}
