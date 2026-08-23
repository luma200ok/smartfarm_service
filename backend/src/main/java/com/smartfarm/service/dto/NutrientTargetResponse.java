package com.smartfarm.service.dto;

/** 목표 ppm(mg/L) 6성분 응답(contract §4.9). */
public record NutrientTargetResponse(double n, double p, double k, double ca, double mg, double s) {
}
