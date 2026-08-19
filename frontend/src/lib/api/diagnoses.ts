// docs/api-contract.md §3(진단 엔드포인트) 래퍼. 전부 authFetch 경유.
import type { DiagnosisResponse, DiagnosisSummaryResponse, PageResponse } from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

export async function createDiagnosis(
  farmId: number | string,
  file: File
): Promise<DiagnosisResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return authFetch<DiagnosisResponse>(ENDPOINTS.farms.diagnoses(farmId), {
    method: "POST",
    body: formData,
  });
}

export async function listDiagnoses(
  farmId: number | string,
  page = 0,
  size = 10
): Promise<PageResponse<DiagnosisSummaryResponse>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return authFetch<PageResponse<DiagnosisSummaryResponse>>(
    `${ENDPOINTS.farms.diagnoses(farmId)}?${query.toString()}`
  );
}

export async function getDiagnosis(
  farmId: number | string,
  diagnosisId: number | string
): Promise<DiagnosisResponse> {
  return authFetch<DiagnosisResponse>(ENDPOINTS.farms.diagnosisDetail(farmId, diagnosisId));
}
