// docs/api-contract.md §3(환경 대시보드 엔드포인트) 래퍼. authFetch 경유.
import type { EnvironmentTodayResponse } from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

export async function getTodayEnvironment(farmId: number | string): Promise<EnvironmentTodayResponse> {
  return authFetch<EnvironmentTodayResponse>(ENDPOINTS.farms.environmentToday(farmId));
}
