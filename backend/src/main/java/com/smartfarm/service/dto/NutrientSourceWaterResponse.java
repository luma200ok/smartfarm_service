package com.smartfarm.service.dto;

/** 원수 분석값 응답(contract §4.9) — 저장하지 않았으면 세 필드 모두 null. */
public record NutrientSourceWaterResponse(Double ca, Double mg, Double ec) {
}
