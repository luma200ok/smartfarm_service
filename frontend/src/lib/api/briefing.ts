// 홈 화면 "오늘 할일" 브리핑(이슈 #129-B) 래퍼. authFetch 경유.
// 농장 단건 기준 집계다 — 여러 농장을 합산하려고 농장 수만큼 반복 호출하지 말 것(N+1,
// 이슈 #142 handoff). 홈 대시보드는 선택된 농장 1건만 조회한다.
import type { FarmBriefingResponse } from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

export async function getFarmBriefing(farmId: number | string): Promise<FarmBriefingResponse> {
  return authFetch<FarmBriefingResponse>(ENDPOINTS.farms.briefing(farmId));
}
