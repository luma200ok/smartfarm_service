// 알람 현황 화면(이슈 #136) 필터 정의 — 시안 헤더의 전체/경보/주의/완료 4칩(단일 선택).
import type { AlarmEventFilters } from "@/lib/api/alarms";
import type { AlarmSeverity } from "@/types";

export type AlarmFilterKey = "ALL" | "CRITICAL" | "WARNING" | "RESOLVED";

// severity·status는 서버에서 AND 결합된다 — "경보"/"주의"는 severity만 걸어 해당 등급으로 생성된
// 모든 이벤트(완료 처리된 것 포함)를 보여주고, "완료"는 status=RESOLVED만 걸어 등급과 무관하게
// 보여준다(이슈 #136 — lib/api/alarms.ts listAlarmEvents 주석 참조, 실제 데이터 모델을 그대로 반영).
export function filterToQuery(filter: AlarmFilterKey): AlarmEventFilters {
  switch (filter) {
    case "CRITICAL":
      return { severity: "CRITICAL" as AlarmSeverity };
    case "WARNING":
      return { severity: "WARNING" as AlarmSeverity };
    case "RESOLVED":
      return { status: "RESOLVED" };
    default:
      return {};
  }
}

export interface FilterCounts {
  ALL: number | null;
  CRITICAL: number | null;
  WARNING: number | null;
  RESOLVED: number | null;
}

export const EMPTY_FILTER_COUNTS: FilterCounts = { ALL: null, CRITICAL: null, WARNING: null, RESOLVED: null };

export const FILTER_CHIPS: { key: AlarmFilterKey; label: string; tone: "neutral" | "critical" | "warning" }[] = [
  { key: "ALL", label: "전체", tone: "neutral" },
  { key: "CRITICAL", label: "경보", tone: "critical" },
  { key: "WARNING", label: "주의", tone: "warning" },
  { key: "RESOLVED", label: "완료", tone: "neutral" },
];
