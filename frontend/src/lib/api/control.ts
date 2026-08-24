// docs/api-contract.md §4.12(제어 도메인, 이슈 #100/#108) 래퍼. 전부 authFetch 경유.
import type {
  ControlApplyRequest,
  ControlApplyResponse,
  ControlChangeRequest,
  ControlChangeResponse,
  ControlModeRequest,
  ControlStateResponse,
  EmergencyStopResponse,
} from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

export async function getControlState(
  farmId: number | string,
  zoneId: number | string
): Promise<ControlStateResponse> {
  return authFetch<ControlStateResponse>(ENDPOINTS.farms.control(farmId, zoneId));
}

export async function changeControlMode(
  farmId: number | string,
  zoneId: number | string,
  payload: ControlModeRequest
): Promise<ControlStateResponse> {
  return authFetch<ControlStateResponse>(ENDPOINTS.farms.controlMode(farmId, zoneId), {
    method: "PUT",
    body: payload,
  });
}

// 큐에 적재만 하고 장비에 즉시 반영하지 않는다(반영은 applyControlChanges).
export async function enqueueControlChange(
  farmId: number | string,
  zoneId: number | string,
  payload: ControlChangeRequest
): Promise<ControlChangeResponse> {
  return authFetch<ControlChangeResponse>(ENDPOINTS.farms.controlChanges(farmId, zoneId), {
    method: "POST",
    body: payload,
  });
}

export async function cancelControlChange(
  farmId: number | string,
  zoneId: number | string,
  changeId: number | string
): Promise<void> {
  return authFetch<void>(ENDPOINTS.farms.controlChangeDetail(farmId, zoneId, changeId), {
    method: "DELETE",
  });
}

export async function cancelAllControlChanges(
  farmId: number | string,
  zoneId: number | string
): Promise<void> {
  return authFetch<void>(ENDPOINTS.farms.controlChanges(farmId, zoneId), { method: "DELETE" });
}

// expectedChangeIds 불일치 시 CT005(409) — 호출부는 errorMessage.ts의 isQueueConflict로
// err.data.pendingChanges를 꺼내 화면 큐를 갱신하고 재확인시킬 것(에러 토스트만 띄우고 끝내지 않는다).
export async function applyControlChanges(
  farmId: number | string,
  zoneId: number | string,
  payload: ControlApplyRequest
): Promise<ControlApplyResponse> {
  return authFetch<ControlApplyResponse>(ENDPOINTS.farms.controlApply(farmId, zoneId), {
    method: "POST",
    body: payload,
  });
}

// 농장 전체 비상 정지(OPERATOR 이상) — 제어기(kind=CONTROLLER)만 끈다. 센서는 계속 측정한다(이슈 #123).
export async function emergencyStop(farmId: number | string): Promise<EmergencyStopResponse> {
  return authFetch<EmergencyStopResponse>(ENDPOINTS.farms.emergencyStop(farmId), { method: "POST" });
}
