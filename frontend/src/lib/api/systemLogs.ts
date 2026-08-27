// docs/api-contract.md §4.17(시스템 로그, 이슈 #129-A) 래퍼 — append-only, 조회 전용. authFetch 경유.
import type { PageResponse, SystemLogCategory, SystemLogResponse } from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

export interface ListSystemLogsParams {
  category?: SystemLogCategory;
  page?: number;
  size?: number;
}

export async function listSystemLogs(
  farmId: number | string,
  params: ListSystemLogsParams = {}
): Promise<PageResponse<SystemLogResponse>> {
  const query = new URLSearchParams();
  if (params.category) query.set("category", params.category);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 20));
  return authFetch<PageResponse<SystemLogResponse>>(`${ENDPOINTS.farms.systemLogs(farmId)}?${query.toString()}`);
}
