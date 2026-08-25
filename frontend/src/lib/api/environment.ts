// docs/api-contract.md §3(환경 대시보드)·§4.6(시계열·임계치, 이슈 #52·#53) 래퍼. authFetch 경유.
import type {
  EnvironmentHistoryRange,
  EnvironmentHistoryResponse,
  EnvironmentTodayResponse,
  EnvThresholdsRequest,
  EnvThresholdsResponse,
  ForecastResponse,
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

// 날씨예보(contract §4.8, 이슈 #57) — 전역 60분 캐시+stale 폴백은 서버가 담당,
// FE는 실패 시(W001 포함) 조용히 unavailable로 대체한다.
export async function getForecast(farmId: number | string): Promise<ForecastResponse> {
  return authFetch<ForecastResponse>(ENDPOINTS.farms.environmentForecast(farmId));
}

export async function getEnvThresholds(farmId: number | string): Promise<EnvThresholdsResponse> {
  return authFetch<EnvThresholdsResponse>(ENDPOINTS.farms.envThresholds(farmId));
}

// ADMIN 전용(호출부에서 myRole 가드) — 서버도 F003으로 재검증(이슈 #123).
export async function updateEnvThresholds(
  farmId: number | string,
  payload: EnvThresholdsRequest
): Promise<EnvThresholdsResponse> {
  return authFetch<EnvThresholdsResponse>(ENDPOINTS.farms.envThresholds(farmId), {
    method: "PUT",
    body: payload,
  });
}
