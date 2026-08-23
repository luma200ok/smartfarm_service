package com.smartfarm.service.dto;

/** 탱크에 투입할 비료 1건(contract §4.9) — fertilizer=국문 표기, formula=화학식. */
public record NutrientTankItemResponse(String fertilizer, String formula, double amountG) {
}
