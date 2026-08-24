// docs/api-contract.md §3(농장·초대·멤버 엔드포인트) 래퍼. 전부 authFetch 경유.
import type {
  AcceptInvitationRequest,
  FarmRequest,
  FarmResponse,
  FarmRole,
  FarmSummaryResponse,
  InvitationResponse,
  MemberResponse,
} from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

export async function listFarms(): Promise<FarmSummaryResponse[]> {
  return authFetch<FarmSummaryResponse[]>(ENDPOINTS.farms.list);
}

export async function createFarm(payload: FarmRequest): Promise<FarmResponse> {
  return authFetch<FarmResponse>(ENDPOINTS.farms.create, { method: "POST", body: payload });
}

export async function getFarm(farmId: number | string): Promise<FarmResponse> {
  return authFetch<FarmResponse>(ENDPOINTS.farms.detail(farmId));
}

export async function updateFarm(
  farmId: number | string,
  payload: Partial<FarmRequest>
): Promise<FarmResponse> {
  return authFetch<FarmResponse>(ENDPOINTS.farms.update(farmId), { method: "PATCH", body: payload });
}

export async function deleteFarm(farmId: number | string): Promise<void> {
  return authFetch<void>(ENDPOINTS.farms.remove(farmId), { method: "DELETE" });
}

export async function createInvitation(farmId: number | string): Promise<InvitationResponse> {
  return authFetch<InvitationResponse>(ENDPOINTS.farms.invitations(farmId), { method: "POST" });
}

export async function acceptInvitation(payload: AcceptInvitationRequest): Promise<FarmResponse> {
  return authFetch<FarmResponse>(ENDPOINTS.invitations.accept, { method: "POST", body: payload });
}

export async function listMembers(farmId: number | string): Promise<MemberResponse[]> {
  return authFetch<MemberResponse[]>(ENDPOINTS.farms.members(farmId));
}

export async function removeMember(
  farmId: number | string,
  memberId: number | string
): Promise<void> {
  return authFetch<void>(ENDPOINTS.farms.removeMember(farmId, memberId), { method: "DELETE" });
}

// 멤버 역할 변경(이슈 #122/#123, ADMIN 전용) — 초대 수락자(PENDING) 승인도 이 호출 하나로
// 처리한다(role을 ADMIN/OPERATOR/VIEWER 중 하나로 부여). 서버가 최종 권한 판정(F003·F006).
export async function updateMemberRole(
  farmId: number | string,
  memberId: number | string,
  role: FarmRole
): Promise<MemberResponse> {
  return authFetch<MemberResponse>(ENDPOINTS.farms.memberRole(farmId, memberId), {
    method: "PATCH",
    body: { role },
  });
}
