// docs/api-contract.md §3(처방 엔드포인트) 래퍼. 전부 authFetch 경유.
import type { PrescriptionRequest, PrescriptionResponse, PrescriptionSummaryResponse, PageResponse } from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

// POST는 202 PrescriptionResponse(PENDING)를 즉시 반환한다 — 폴링은 호출부(컴포넌트)의 책임.
export async function createPrescription(
  farmId: number | string,
  payload: PrescriptionRequest
): Promise<PrescriptionResponse> {
  return authFetch<PrescriptionResponse>(ENDPOINTS.farms.prescriptions(farmId), {
    method: "POST",
    body: payload,
  });
}

export async function listPrescriptions(
  farmId: number | string,
  page = 0,
  size = 10
): Promise<PageResponse<PrescriptionSummaryResponse>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return authFetch<PageResponse<PrescriptionSummaryResponse>>(
    `${ENDPOINTS.farms.prescriptions(farmId)}?${query.toString()}`
  );
}

export async function getPrescription(
  farmId: number | string,
  prescriptionId: number | string
): Promise<PrescriptionResponse> {
  return authFetch<PrescriptionResponse>(ENDPOINTS.farms.prescriptionDetail(farmId, prescriptionId));
}
