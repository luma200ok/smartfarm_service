// docs/api-contract.md §4.13(알람 이벤트·규칙, 이슈 #116/#118) 래퍼. 전부 authFetch 경유.
import type {
  AlarmAcknowledgeAllResponse,
  AlarmEventDetailResponse,
  AlarmEventResponse,
  AlarmEventStatus,
  AlarmRuleResponse,
  AlarmSeverity,
  AlarmStatsResponse,
  AlarmUnacknowledgedCountResponse,
  PageResponse,
} from "@/types";
import { authFetch } from "./auth";
import { ENDPOINTS } from "./endpoints";

export interface AlarmEventFilters {
  status?: AlarmEventStatus;
  severity?: AlarmSeverity;
}

// status·severity는 서버에서 AND로 결합된다(AlarmEventRepositoryImpl#buildPredicates) — 둘 다
// 주면 "해당 등급이면서 해당 상태"만 남는다. 알람 화면의 등급 필터(경보/주의)는 severity만,
// 완료 필터는 status=RESOLVED만 보낸다(이슈 #136 — severity는 생성 시 고정이라 해소된 알람도
// 원래 등급을 유지하므로, "경보" 필터에도 완료 처리된 경보가 함께 보일 수 있다. 이는 실제
// 데이터를 그대로 반영한 것으로 의도된 동작이다).
export async function listAlarmEvents(
  farmId: number | string,
  filters: AlarmEventFilters = {},
  page = 0,
  size = 20
): Promise<PageResponse<AlarmEventResponse>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (filters.status) query.set("status", filters.status);
  if (filters.severity) query.set("severity", filters.severity);
  return authFetch<PageResponse<AlarmEventResponse>>(`${ENDPOINTS.farms.alarmEvents(farmId)}?${query.toString()}`);
}

export async function getAlarmEvent(
  farmId: number | string,
  alarmEventId: number | string
): Promise<AlarmEventDetailResponse> {
  return authFetch<AlarmEventDetailResponse>(ENDPOINTS.farms.alarmEventDetail(farmId, alarmEventId));
}

// OPERATOR 이상(서버 F007로 최종 판정 — 버튼 노출은 보조, 이슈 #122/#123 원칙).
export async function acknowledgeAlarmEvent(
  farmId: number | string,
  alarmEventId: number | string
): Promise<AlarmEventResponse> {
  return authFetch<AlarmEventResponse>(ENDPOINTS.farms.alarmEventAcknowledge(farmId, alarmEventId), {
    method: "PATCH",
  });
}

// OPERATOR 이상. ACKNOWLEDGED 상태에서만 허용(그 외 상태면 서버가 AL002).
export async function resolveAlarmEvent(
  farmId: number | string,
  alarmEventId: number | string
): Promise<AlarmEventResponse> {
  return authFetch<AlarmEventResponse>(ENDPOINTS.farms.alarmEventResolve(farmId, alarmEventId), {
    method: "POST",
  });
}

// OPERATOR 이상. 응답에 갱신된 타임라인 전체가 실려오므로 별도 재조회가 필요 없다.
export async function addAlarmMemo(
  farmId: number | string,
  alarmEventId: number | string,
  note: string
): Promise<AlarmEventDetailResponse> {
  return authFetch<AlarmEventDetailResponse>(ENDPOINTS.farms.alarmEventMemo(farmId, alarmEventId), {
    method: "POST",
    body: { note },
  });
}

// OPERATOR 이상. 미확인 알람 전량을 확인 처리한다(확인 모달 후 호출).
export async function acknowledgeAllAlarmEvents(farmId: number | string): Promise<AlarmAcknowledgeAllResponse> {
  return authFetch<AlarmAcknowledgeAllResponse>(ENDPOINTS.farms.alarmEventsAcknowledgeAll(farmId), {
    method: "POST",
  });
}

export async function getAlarmStats(farmId: number | string, days = 7): Promise<AlarmStatsResponse> {
  const query = new URLSearchParams({ days: String(days) });
  return authFetch<AlarmStatsResponse>(`${ENDPOINTS.farms.alarmEventsStats(farmId)}?${query.toString()}`);
}

// TopBar 배지용 경량 조회(멤버 누구나).
export async function getUnacknowledgedCount(
  farmId: number | string
): Promise<AlarmUnacknowledgedCountResponse> {
  return authFetch<AlarmUnacknowledgedCountResponse>(ENDPOINTS.farms.alarmEventsUnacknowledgedCount(farmId));
}

// 상세 패널 "규칙" 한 줄 요약용 단건 조회(멤버 누구나). ruleId가 없거나 조회에 실패하면
// 호출부가 그 행 자체를 생략한다 — 없는 규칙 정보를 지어내지 않는다(이슈 #136 핸드오프).
export async function getAlarmRule(
  farmId: number | string,
  ruleId: number | string
): Promise<AlarmRuleResponse> {
  return authFetch<AlarmRuleResponse>(ENDPOINTS.farms.alarmRuleDetail(farmId, ruleId));
}
