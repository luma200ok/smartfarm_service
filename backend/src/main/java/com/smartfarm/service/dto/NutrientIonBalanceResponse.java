package com.smartfarm.service.dto;

/** 이온 밸런스(contract §4.9) — 목표(원수 보정 후) ppm 기준 me/L. */
public record NutrientIonBalanceResponse(double cationMeL, double anionMeL, double deviationPercent) {
}
