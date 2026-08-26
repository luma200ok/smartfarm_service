// 알람 현황 화면(이슈 #136) 전용 순수 표시 헬퍼. 컴포넌트에서 분리해 테스트하기 쉽게 한다.
import { ALARM_COMPARATOR_LABELS, SENSOR_METRIC_LABELS } from "@/constants";
import type { AlarmEventStatus, AlarmRuleResponse, AlarmScopeType, AlarmSeverity, SensorMetric } from "@/types";
import type { LocationMaps } from "./zoneTree";

/**
 * 목록/상세의 등급 표시 톤 — 처리 완료(RESOLVED)는 원래 severity와 무관하게 "done"으로 표시한다
 * (시안 05.패턴). severity 자체는 생성 시 고정값이라 바뀌지 않지만, 화면 표시는 상태를 우선한다.
 */
export type AlarmDisplayTone = "critical" | "warning" | "done";

export function alarmDisplayTone(severity: AlarmSeverity, status: AlarmEventStatus): AlarmDisplayTone {
  if (status === "RESOLVED") return "done";
  return severity === "CRITICAL" ? "critical" : "warning";
}

export function alarmToneTextClass(tone: AlarmDisplayTone): string {
  return tone === "critical" ? "text-dp-red-ink" : tone === "warning" ? "text-dp-amber-deep" : "text-dp-green";
}

/** 같은 날이면 "HH:mm", 아니면 "MM.DD" — 목록 행·타임라인 공용(핸드오프 시안 폭 제약 반영). */
export function formatAlarmTimestamp(iso: string): string {
  const date = new Date(iso);
  const now = new Date();
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate();
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  if (sameDay) return `${hh}:${mm}`;
  const MM = String(date.getMonth() + 1).padStart(2, "0");
  const DD = String(date.getDate()).padStart(2, "0");
  return `${MM}.${DD}`;
}

/** 상세 패널 "발생" 필드 — 날짜까지 포함한 전체 표기. */
export function formatAlarmDateTime(iso: string): string {
  const date = new Date(iso);
  const yyyy = date.getFullYear();
  const MM = String(date.getMonth() + 1).padStart(2, "0");
  const DD = String(date.getDate()).padStart(2, "0");
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  return `${yyyy}.${MM}.${DD} ${hh}:${mm}`;
}

interface ScopeInput {
  scopeType: AlarmScopeType | null;
  scopeId: number | null;
}

/**
 * 알람의 "위치"를 zone/rack/level 이름으로 조합한다(핸드오프 "없는 데이터를 지어내지 말 것" §위치).
 * - scopeType이 null이거나 "FARM"이면 농장명만(#118 이전 이벤트는 scopeType 자체가 null).
 * - 그 외는 zoneTree 조회 맵에서 이름을 찾아 "존 · 랙 · 층"으로 조합, 못 찾으면(구조 삭제 등) "—".
 * - farmName·maps 자체가 없으면(조회 실패) 마찬가지로 "—".
 */
export function describeAlarmScope(
  scope: ScopeInput,
  maps: LocationMaps | null,
  farmName: string | null
): string {
  if (scope.scopeType === null || scope.scopeType === "FARM") {
    return farmName ?? "—";
  }
  if (!maps || scope.scopeId === null) return "—";

  if (scope.scopeType === "ZONE") {
    return maps.zoneNameById.get(scope.scopeId) ?? "—";
  }
  if (scope.scopeType === "RACK") {
    const rack = maps.rackById.get(scope.scopeId);
    if (!rack) return "—";
    const zoneName = maps.zoneNameById.get(rack.zoneId);
    const joined = [zoneName, rack.code].filter(Boolean).join(" · ");
    return joined || "—";
  }
  // LEVEL
  const level = maps.levelById.get(scope.scopeId);
  if (!level) return "—";
  const rack = maps.rackById.get(level.rackId);
  const zoneName = rack ? maps.zoneNameById.get(rack.zoneId) : undefined;
  const joined = [zoneName, rack?.code, level.label].filter(Boolean).join(" · ");
  return joined || "—";
}

/**
 * 알람 규칙 한 줄 요약 — 실제 규칙 필드(metric·comparator·threshold·durationSeconds)만 조합한다.
 * 시안의 예시 문구("원수 혼합 밸브 응답 지연…")처럼 근거 없는 원인 설명을 만들지 않는다.
 */
export function summarizeAlarmRule(rule: AlarmRuleResponse): string {
  const metricLabel = rule.metric ? (SENSOR_METRIC_LABELS[rule.metric as SensorMetric] ?? rule.metric) : "장비 응답";
  const comparatorLabel = ALARM_COMPARATOR_LABELS[rule.comparator];

  let valuePart = "";
  if (rule.comparator === "GT" || rule.comparator === "LT") {
    if (rule.thresholdValue !== null) valuePart = ` ${rule.thresholdValue}`;
  } else if (rule.comparator === "OUTSIDE_RANGE") {
    if (rule.thresholdMin !== null && rule.thresholdMax !== null) {
      valuePart = ` ${rule.thresholdMin}~${rule.thresholdMax}`;
    }
  }

  const durationPart = rule.durationSeconds ? ` · ${Math.round(rule.durationSeconds / 60)}분 지속` : "";

  return `${metricLabel} ${comparatorLabel}${valuePart}${durationPart}`.trim();
}
