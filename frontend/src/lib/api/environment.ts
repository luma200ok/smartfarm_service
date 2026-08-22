// docs/api-contract.md §3(환경 대시보드)·§4.6(시계열·임계치, 이슈 #52·#53) 래퍼. authFetch 경유.
import type {
  EnvironmentHistoryRange,
  EnvironmentHistoryResponse,
  EnvironmentTodayResponse,
  EnvThresholdsRequest,
  EnvThresholdsResponse,
} from "@/types";
import { authFetch } from "./auth";
import type { ApiRequestOptions } from "./client";
import { ENDPOINTS } from "./endpoints";

export async function getTodayEnvironment(farmId: number | string): Promise<EnvironmentTodayResponse> {
  return authFetch<EnvironmentTodayResponse>(ENDPOINTS.farms.environmentToday(farmId));
}

// options.signal — 기간 탭 전환 시 이전 요청을 취소하기 위한 AbortSignal 관통(리뷰 픽스 #53 P2-2).
export async function getEnvironmentHistory(
  farmId: number | string,
  range: EnvironmentHistoryRange,
  options?: Pick<ApiRequestOptions, "signal">
): Promise<EnvironmentHistoryResponse> {
  const query = new URLSearchParams({ range });
  return authFetch<EnvironmentHistoryResponse>(
    `${ENDPOINTS.farms.environmentHistory(farmId)}?${query.toString()}`,
    options
  );
}

export async function getEnvThresholds(farmId: number | string): Promise<EnvThresholdsResponse> {
  return authFetch<EnvThresholdsResponse>(ENDPOINTS.farms.envThresholds(farmId));
}

// OWNER 전용(호출부에서 myRole 가드) — 서버도 F003으로 재검증.
export async function updateEnvThresholds(
  farmId: number | string,
  payload: EnvThresholdsRequest
): Promise<EnvThresholdsResponse> {
  return authFetch<EnvThresholdsResponse>(ENDPOINTS.farms.envThresholds(farmId), {
    method: "PUT",
    body: payload,
  });
}
