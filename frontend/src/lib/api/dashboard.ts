// 홈 대시보드 집계(이슈 #139) 래퍼. authFetch 경유.
// 내 활성 농장 카드 전체를 한 번에 반환한다 — 농장마다 zones/devices/readings를 개별 조회하면
// 이 API를 만든 의미가 없어진다(이슈 #142 handoff). 화면은 이 응답 하나로 카드 그리드를 채운다.
// 응답은 평문 배열이 아니라 { farms, totalCount, truncated } 래퍼다(이슈 #140) — 집계 상한
// 초과로 카드가 잘렸는지를 화면이 알 수 있도록 백엔드가 명시적 신호를 얹었다.
import type { DashboardFarmsResponse } from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

export async function getDashboardFarms(): Promise<DashboardFarmsResponse> {
  return authFetch<DashboardFarmsResponse>(ENDPOINTS.dashboard.farms);
}
