// 농장 역할 서열 헬퍼(이슈 #123, contract §2) — 백엔드 FarmRole#rank/atLeast/isActive와
// 이름·서열을 동일하게 맞춘다. `myRole === "ADMIN"` 같은 단순 비교를 컴포넌트마다 복붙하면
// 다음 역할 체계 변경 때 한쪽만 고쳐지는 사고가 나므로, 게이트는 전부 이 모듈을 거친다.
import type { FarmRole } from "@/types";

// 클수록 강한 권한. ordinal()류(선언 순서)에 기대지 않고 명시적으로 고정한다.
export const FARM_ROLE_RANK: Record<FarmRole, number> = {
  ADMIN: 3,
  OPERATOR: 2,
  VIEWER: 1,
  PENDING: 0,
};

/**
 * role이 required 이상의 서열인가(ADMIN은 OPERATOR 요구를 항상 통과).
 * role이 아직 로드되지 않았으면(null/undefined) 항상 false — 로딩 중 관리자 UI를 잠깐
 * 노출했다가 감추는 깜빡임을 막는다(권한 UI는 항상 "보수적으로 숨김"이 기본값).
 */
export function hasFarmRoleAtLeast(role: FarmRole | null | undefined, required: FarmRole): boolean {
  if (!role) return false;
  return FARM_ROLE_RANK[role] >= FARM_ROLE_RANK[required];
}

/** 승인된 멤버인가 — PENDING만 false. farm-scoped 화면 진입 최소 자격. */
export function isActiveFarmRole(role: FarmRole | null | undefined): boolean {
  return role != null && role !== "PENDING";
}
