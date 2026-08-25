// docs/api-contract.md §4.8(작업일지 엔드포인트, 이슈 #56·#57) 래퍼. 전부 authFetch 경유.
import type { FarmLogRequest, FarmLogResponse, PageResponse } from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

export async function createFarmLog(
  farmId: number | string,
  payload: FarmLogRequest
): Promise<FarmLogResponse> {
  return authFetch<FarmLogResponse>(ENDPOINTS.farms.logs(farmId), {
    method: "POST",
    body: payload,
  });
}

export async function listFarmLogs(
  farmId: number | string,
  page = 0,
  size = 10
): Promise<PageResponse<FarmLogResponse>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return authFetch<PageResponse<FarmLogResponse>>(`${ENDPOINTS.farms.logs(farmId)}?${query.toString()}`);
}

// 작성자 본인만(서버 L002로 최종 판정 — 버튼 노출은 보조).
export async function updateFarmLog(
  farmId: number | string,
  logId: number | string,
  payload: FarmLogRequest
): Promise<FarmLogResponse> {
  return authFetch<FarmLogResponse>(ENDPOINTS.farms.logDetail(farmId, logId), {
    method: "PATCH",
    body: payload,
  });
}

// 작성자 본인 또는 ADMIN(서버 L002로 최종 판정 — 버튼 노출은 보조, 이슈 #123).
export async function deleteFarmLog(farmId: number | string, logId: number | string): Promise<void> {
  return authFetch<void>(ENDPOINTS.farms.logDetail(farmId, logId), { method: "DELETE" });
}
