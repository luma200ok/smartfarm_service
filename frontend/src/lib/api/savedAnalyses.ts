// docs/api-contract.md §4.15(저장한 분석, 이슈 #126) 래퍼. 전부 authFetch 경유.
import type { SavedAnalysisRequest, SavedAnalysisResponse, SavedAnalysisUpdateRequest } from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

export async function listSavedAnalyses(farmId: number | string): Promise<SavedAnalysisResponse[]> {
  return authFetch<SavedAnalysisResponse[]>(ENDPOINTS.farms.savedAnalyses(farmId));
}

// OPERATOR 이상 — 호출부에서 hasFarmRoleAtLeast(role, "OPERATOR")로 가드(서버도 F003으로 재검증).
export async function createSavedAnalysis(
  farmId: number | string,
  payload: SavedAnalysisRequest
): Promise<SavedAnalysisResponse> {
  return authFetch<SavedAnalysisResponse>(ENDPOINTS.farms.savedAnalyses(farmId), {
    method: "POST",
    body: payload,
  });
}

// 작성자 본인 또는 ADMIN — name만 수정 가능(서버 SavedAnalysisUpdateRequest 규약).
export async function renameSavedAnalysis(
  farmId: number | string,
  analysisId: number | string,
  payload: SavedAnalysisUpdateRequest
): Promise<SavedAnalysisResponse> {
  return authFetch<SavedAnalysisResponse>(ENDPOINTS.farms.savedAnalysisDetail(farmId, analysisId), {
    method: "PATCH",
    body: payload,
  });
}

export async function deleteSavedAnalysis(farmId: number | string, analysisId: number | string): Promise<void> {
  return authFetch<void>(ENDPOINTS.farms.savedAnalysisDetail(farmId, analysisId), { method: "DELETE" });
}
