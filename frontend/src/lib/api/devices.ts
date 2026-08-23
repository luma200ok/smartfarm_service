// docs/api-contract.md §4.10(장비/센서 레지스트리, 이슈 #89) 래퍼. 전부 authFetch 경유.
import type {
  DeviceKind,
  DeviceListResponse,
  DeviceRequest,
  DeviceResponse,
  DeviceStatus,
  DeviceSummaryResponse,
} from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

export interface ListDevicesParams {
  kind?: DeviceKind;
  status?: DeviceStatus;
  /** 장비명 부분일치 */
  q?: string;
  zoneId?: number;
}

export async function listDevices(
  farmId: number | string,
  params: ListDevicesParams = {}
): Promise<DeviceListResponse> {
  const query = new URLSearchParams();
  if (params.kind) query.set("kind", params.kind);
  if (params.status) query.set("status", params.status);
  if (params.q) query.set("q", params.q);
  if (params.zoneId !== undefined) query.set("zoneId", String(params.zoneId));
  const qs = query.toString();
  return authFetch<DeviceListResponse>(`${ENDPOINTS.farms.devices(farmId)}${qs ? `?${qs}` : ""}`);
}

export async function getDeviceSummary(farmId: number | string): Promise<DeviceSummaryResponse> {
  return authFetch<DeviceSummaryResponse>(ENDPOINTS.farms.deviceSummary(farmId));
}

export async function createDevice(
  farmId: number | string,
  payload: DeviceRequest
): Promise<DeviceResponse> {
  return authFetch<DeviceResponse>(ENDPOINTS.farms.devices(farmId), { method: "POST", body: payload });
}

export async function updateDevice(
  farmId: number | string,
  deviceId: number | string,
  payload: DeviceRequest
): Promise<DeviceResponse> {
  return authFetch<DeviceResponse>(ENDPOINTS.farms.deviceDetail(farmId, deviceId), {
    method: "PATCH",
    body: payload,
  });
}

export async function deleteDevice(farmId: number | string, deviceId: number | string): Promise<void> {
  return authFetch<void>(ENDPOINTS.farms.deviceDetail(farmId, deviceId), { method: "DELETE" });
}
