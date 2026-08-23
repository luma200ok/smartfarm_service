// docs/api-contract.md §4.9(양액 프리셋, 이슈 #64·#65) 래퍼. 전부 authFetch 경유.
// 프리셋은 DB가 아닌 백엔드 코드 상수(NutrientPresets.java)라 GET 조회만 제공한다.
import type { CropType, NutrientPresetResponse } from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

// cropType은 계약상 1차 TOMATO 고정(§4.9) — 확장 대비 매개변수는 남겨둔다.
export async function listNutrientPresets(cropType: CropType = "TOMATO"): Promise<NutrientPresetResponse[]> {
  const query = new URLSearchParams({ cropType });
  return authFetch<NutrientPresetResponse[]>(`${ENDPOINTS.nutrientPresets}?${query.toString()}`);
}
