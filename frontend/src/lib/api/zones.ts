// docs/api-contract.md §4.10(존·랙 구조, 이슈 #89) 래퍼. 전부 authFetch 경유.
// 존·랙 CRUD(이슈 #99 리뷰 반영) — 진입로 없이는 신규 사용자가 랙 도면·장비 등록 화면을
// 영영 못 쓰므로 devices 화면에 최소 범위(이름·코드·층수)로 붙인다.
import type {
  RackRequest,
  RackResponse,
  RackUpdateRequest,
  ZoneRequest,
  ZoneResponse,
  ZoneTreeResponse,
  ZoneUpdateRequest,
} from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

export async function getZoneTree(farmId: number | string): Promise<ZoneTreeResponse> {
  return authFetch<ZoneTreeResponse>(ENDPOINTS.farms.zones(farmId));
}

export async function createZone(farmId: number | string, payload: ZoneRequest): Promise<ZoneResponse> {
  return authFetch<ZoneResponse>(ENDPOINTS.farms.zones(farmId), { method: "POST", body: payload });
}

export async function updateZone(
  farmId: number | string,
  zoneId: number | string,
  payload: ZoneUpdateRequest
): Promise<ZoneResponse> {
  return authFetch<ZoneResponse>(ENDPOINTS.farms.zoneDetail(farmId, zoneId), { method: "PATCH", body: payload });
}

// 하위 랙·층 함께 soft delete. 하위에 활성 장비가 남아 있으면 서버가 409 R004로 거부한다
// (조용한 데이터 유실 방지 — 호출부는 resolveErrorMessage로 R004 문구를 그대로 노출할 것).
export async function deleteZone(farmId: number | string, zoneId: number | string): Promise<void> {
  return authFetch<void>(ENDPOINTS.farms.zoneDetail(farmId, zoneId), { method: "DELETE" });
}

// levelCount만큼 층이 서버에서 자동 생성된다.
export async function createRack(
  farmId: number | string,
  zoneId: number | string,
  payload: RackRequest
): Promise<RackResponse> {
  return authFetch<RackResponse>(ENDPOINTS.farms.racksUnderZone(farmId, zoneId), {
    method: "POST",
    body: payload,
  });
}

// levelCount 축소 시 잘려나가는 층에 활성 장비가 있으면 서버가 409 R004로 거부한다.
export async function updateRack(
  farmId: number | string,
  rackId: number | string,
  payload: RackUpdateRequest
): Promise<RackResponse> {
  return authFetch<RackResponse>(ENDPOINTS.farms.rackDetail(farmId, rackId), { method: "PATCH", body: payload });
}

// 하위 층 함께 soft delete. 하위에 활성 장비가 남아 있으면 서버가 409 R004로 거부한다.
export async function deleteRack(farmId: number | string, rackId: number | string): Promise<void> {
  return authFetch<void>(ENDPOINTS.farms.rackDetail(farmId, rackId), { method: "DELETE" });
}
