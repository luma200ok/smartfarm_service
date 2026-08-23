package com.smartfarm.service.dto;

import java.util.List;

/** 탱크별 투입 목록(contract §4.9) — tank는 "A" 또는 "B". */
public record NutrientTankAllocationResponse(String tank, List<NutrientTankItemResponse> items) {
}
