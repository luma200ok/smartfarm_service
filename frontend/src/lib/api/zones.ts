// docs/api-contract.md §4.10(존·랙 구조, 이슈 #89) 래퍼. 전부 authFetch 경유.
// 존·랙 CRUD 화면("농장·랙 구성" 관리)은 이번 사이클(#99) 범위 밖이라 조회만 둔다.
import type { ZoneTreeResponse } from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

export async function getZoneTree(farmId: number | string): Promise<ZoneTreeResponse> {
  return authFetch<ZoneTreeResponse>(ENDPOINTS.farms.zones(farmId));
}
