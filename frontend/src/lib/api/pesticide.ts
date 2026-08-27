// docs/api-contract.md §4.16(농약 참조정보, 이슈 #128) 래퍼 — farm-scoped 아님(인증만), authFetch 경유.
// ⚠️ 응답의 source는 항상 "내부 시드 샘플" 고지를 담고 있다 — 화면은 이 값을 그대로 표시해야 하고
// 임의 문구로 대체하면 안 된다(계약 §4.16 "출처 표기 규칙 — 안전").
import type { CropType, PesticideAlertResponse, PesticideReferenceResponse } from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

export async function listPesticideReferences(
  cropType: CropType,
  q?: string
): Promise<PesticideReferenceResponse[]> {
  const query = new URLSearchParams({ cropType });
  if (q) query.set("q", q);
  return authFetch<PesticideReferenceResponse[]>(`${ENDPOINTS.pesticideReferences.list}?${query.toString()}`);
}

export async function listPesticideAlerts(cropType: CropType): Promise<PesticideAlertResponse[]> {
  const query = new URLSearchParams({ cropType });
  return authFetch<PesticideAlertResponse[]>(`${ENDPOINTS.pesticideReferences.alerts}?${query.toString()}`);
}
