// docs/api-contract.md §4.9(양액 배합 계산/레시피, 이슈 #64·#65) 래퍼. 전부 authFetch 경유.
// 비료 투입량·EC·이온밸런스 계산은 전부 서버(NutrientCalculationEngine)가 수행 —
// 이 파일은 요청/응답을 그대로 전달만 하고 계산을 재구현하지 않는다.
import type {
  NutrientCalculationResponse,
  NutrientRecipeRequest,
  NutrientRecipeResponse,
  NutrientRecipeSaveRequest,
  NutrientRecipeSummaryResponse,
  PageResponse,
} from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

// 저장 없이 미리보기(멤버, 데모 계정도 허용).
export async function calculateNutrientRecipe(
  farmId: number | string,
  payload: NutrientRecipeRequest
): Promise<NutrientCalculationResponse> {
  return authFetch<NutrientCalculationResponse>(ENDPOINTS.farms.nutrientCalculate(farmId), {
    method: "POST",
    body: payload,
  });
}

// name 필수(NutrientRecipeSaveRequest) — calculate와 달리 저장 경로라 타입 레벨에서 강제한다.
export async function createNutrientRecipe(
  farmId: number | string,
  payload: NutrientRecipeSaveRequest
): Promise<NutrientRecipeResponse> {
  return authFetch<NutrientRecipeResponse>(ENDPOINTS.farms.nutrientRecipes(farmId), {
    method: "POST",
    body: payload,
  });
}

export async function listNutrientRecipes(
  farmId: number | string,
  page = 0,
  size = 10
): Promise<PageResponse<NutrientRecipeSummaryResponse>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return authFetch<PageResponse<NutrientRecipeSummaryResponse>>(
    `${ENDPOINTS.farms.nutrientRecipes(farmId)}?${query.toString()}`
  );
}

export async function getNutrientRecipe(
  farmId: number | string,
  recipeId: number | string
): Promise<NutrientRecipeResponse> {
  return authFetch<NutrientRecipeResponse>(ENDPOINTS.farms.nutrientRecipeDetail(farmId, recipeId));
}

// 작성자 본인만(서버 N002로 최종 판정 — 버튼 노출은 보조). name 필수(NutrientRecipeSaveRequest).
export async function updateNutrientRecipe(
  farmId: number | string,
  recipeId: number | string,
  payload: NutrientRecipeSaveRequest
): Promise<NutrientRecipeResponse> {
  return authFetch<NutrientRecipeResponse>(ENDPOINTS.farms.nutrientRecipeDetail(farmId, recipeId), {
    method: "PATCH",
    body: payload,
  });
}

// 작성자 본인 또는 OWNER(서버 N002로 최종 판정 — 버튼 노출은 보조).
export async function deleteNutrientRecipe(farmId: number | string, recipeId: number | string): Promise<void> {
  return authFetch<void>(ENDPOINTS.farms.nutrientRecipeDetail(farmId, recipeId), { method: "DELETE" });
}
