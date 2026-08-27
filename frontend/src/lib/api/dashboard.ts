// 홈 대시보드 집계(이슈 #139) 래퍼. authFetch 경유.
// 내 활성 농장 카드 전체를 한 번에 반환한다 — 농장마다 zones/devices/readings를 개별 조회하면
// 이 API를 만든 의미가 없어진다(이슈 #142 handoff). 화면은 이 응답 하나로 카드 그리드를 채운다.
import type { FarmDashboardResponse } from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

export async function getDashboardFarms(): Promise<FarmDashboardResponse[]> {
  return authFetch<FarmDashboardResponse[]>(ENDPOINTS.dashboard.farms);
}
