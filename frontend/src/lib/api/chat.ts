// docs/api-contract.md §4.7(AI 챗봇 엔드포인트, 이슈 #54·#55) 래퍼. 전부 authFetch 경유.
import type { ChatMessageResponse, ChatRequest, PageResponse } from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

// POST는 동기 200 ChatMessageResponse를 반환한다(백엔드 타임아웃 30s, contract §4.7).
export async function sendChatMessage(
  farmId: number | string,
  payload: ChatRequest
): Promise<ChatMessageResponse> {
  return authFetch<ChatMessageResponse>(ENDPOINTS.farms.chat(farmId), {
    method: "POST",
    body: payload,
  });
}

// 최신순 페이지 응답 — 화면에 시간순(오래된 순)으로 보여주는 정렬은 호출부 책임.
export async function listChatMessages(
  farmId: number | string,
  page = 0,
  size = 20
): Promise<PageResponse<ChatMessageResponse>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return authFetch<PageResponse<ChatMessageResponse>>(`${ENDPOINTS.farms.chat(farmId)}?${query.toString()}`);
}
