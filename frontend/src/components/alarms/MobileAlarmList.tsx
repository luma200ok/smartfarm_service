"use client";

import Link from "next/link";
import { ALARM_STATUS_LABELS } from "@/constants";
import {
  alarmDisplayTone,
  alarmToneTextClass,
  describeAlarmScope,
  formatAlarmTimestamp,
} from "@/lib/alarmDisplay";
import type { LocationMaps } from "@/lib/zoneTree";
import type { AlarmEventResponse } from "@/types";
import type { AlarmFilterKey, FilterCounts } from "./filters";
import { FILTER_CHIPS } from "./filters";

interface MobileAlarmListProps {
  farmId: string;
  filter: AlarmFilterKey;
  items: AlarmEventResponse[];
  filterCounts: FilterCounts;
  loading: boolean;
  loadingMore: boolean;
  totalElements: number;
  error: string | null;
  canWrite: boolean;
  actionBusy: boolean;
  unacknowledgedTotal: number | null;
  onSelect: (id: number) => void;
  onFilterChange: (key: AlarmFilterKey) => void;
  onAcknowledge: (id: number) => void;
  onAcknowledgeAll: () => void;
  onLoadMore: () => void;
  onShowAll: () => void;
  onRefresh: () => void;
  locationMaps: LocationMaps | null;
  farmName: string | null;
}

// 모바일 알람 화면(이슈 #147, 시안 m3-alarm) — 카드 스택. 좌측 3px 세로 바로 등급, 미확인
// 최상위 1건에만 "확인"/"제어 이동" 액션을 얹는다(핸드오프 §2). 나머지는 탭하면 상세 오버레이.
// items·필터·필터카운트는 AlarmsPageClient가 이미 들고 있는 상태를 그대로 받는다(재조회 없음).
export default function MobileAlarmList({
  farmId,
  filter,
  items,
  filterCounts,
  loading,
  loadingMore,
  totalElements,
  error,
  canWrite,
  actionBusy,
  unacknowledgedTotal,
  onSelect,
  onFilterChange,
  onAcknowledge,
  onAcknowledgeAll,
  onLoadMore,
  onShowAll,
  onRefresh,
  locationMaps,
  farmName,
}: MobileAlarmListProps) {
  const topUnacknowledgedId =
    items.find((it) => it.status === "UNACKNOWLEDGED")?.id ?? null;

  return (
    <div className="flex flex-col">
      {/* 화면명(알람 현황)은 상단 다크 바(MobileTopBar, sub variant)가 이미 보여준다 — 여기는
          그 바에 넣지 못한 "액션 하나"(전체 확인)만 얹는다(MobileTopBar.tsx 주석 참조). */}
      {canWrite && (
        <div className="flex justify-end px-4 pt-3">
          <button
            type="button"
            onClick={onAcknowledgeAll}
            disabled={unacknowledgedTotal === 0}
            className="min-h-11 rounded-[7px] border border-dp-line-strong px-3 text-[12px] font-medium text-dp-body disabled:opacity-40"
          >
            전체 확인 처리
          </button>
        </div>
      )}

      <div className="flex gap-1.5 overflow-x-auto px-4 py-3">
        {FILTER_CHIPS.map((chip) => {
          const count = filterCounts[chip.key];
          const active = chip.key === filter;
          return (
            <button
              key={chip.key}
              type="button"
              onClick={() => onFilterChange(chip.key)}
              className={`min-h-11 flex-none rounded-[7px] px-[13px] text-[12px] font-medium whitespace-nowrap ${
                active
                  ? "bg-dp-ink text-dp-surface"
                  : "border border-dp-line-strong text-dp-body"
              }`}
            >
              {chip.label}
              {count !== null ? ` ${count}` : ""}
            </button>
          );
        })}
      </div>

      {error && (
        <p className="px-4 py-4 text-[12.5px] text-dp-red-ink">{error}</p>
      )}

      {!error && loading && (
        <p className="px-4 py-6 text-[12.5px] text-dp-muted">불러오는 중...</p>
      )}

      {!error && !loading && items.length === 0 && (
        <div className="flex flex-col items-center gap-3 px-4 py-10 text-center">
          <p className="text-[12.5px] text-dp-sub">
            {filter === "ALL"
              ? "등록된 알람이 없습니다."
              : "이 조건에 해당하는 알람이 없습니다."}
          </p>
          <button
            type="button"
            onClick={filter === "ALL" ? onRefresh : onShowAll}
            className="min-h-11 rounded-[7px] border border-dp-line-strong px-3.5 text-[12.5px] font-semibold text-dp-body"
          >
            {filter === "ALL" ? "새로고침" : "전체 보기"}
          </button>
        </div>
      )}

      {!error && !loading && items.length > 0 && (
        <div className="flex flex-col gap-2.5 px-4 pb-4">
          {items.map((alarm) => (
            <AlarmCard
              key={alarm.id}
              farmId={farmId}
              alarm={alarm}
              location={describeAlarmScope(alarm, locationMaps, farmName)}
              isTopUnacknowledged={alarm.id === topUnacknowledgedId}
              canWrite={canWrite}
              actionBusy={actionBusy}
              onSelect={() => onSelect(alarm.id)}
              onAcknowledge={() => onAcknowledge(alarm.id)}
            />
          ))}
          {items.length < totalElements && (
            <button
              type="button"
              onClick={onLoadMore}
              disabled={loadingMore}
              className="min-h-11 text-[12.5px] font-semibold text-dp-green disabled:opacity-50"
            >
              {loadingMore
                ? "불러오는 중..."
                : `${totalElements}건 중 ${items.length}건 표시 · 더 보기`}
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function AlarmCard({
  farmId,
  alarm,
  location,
  isTopUnacknowledged,
  canWrite,
  actionBusy,
  onSelect,
  onAcknowledge,
}: {
  farmId: string;
  alarm: AlarmEventResponse;
  location: string;
  isTopUnacknowledged: boolean;
  canWrite: boolean;
  actionBusy: boolean;
  onSelect: () => void;
  onAcknowledge: () => void;
}) {
  const tone = alarmDisplayTone(alarm.severity, alarm.status);
  const isDone = tone === "done";
  const barClass =
    tone === "critical"
      ? "border-l-dp-red"
      : tone === "warning"
        ? "border-l-dp-amber"
        : "border-l-dp-line-strong";

  return (
    <div
      className={`rounded-[10px] border border-dp-line border-l-[3px] bg-dp-surface p-[14px] ${barClass}`}
    >
      <button
        type="button"
        onClick={onSelect}
        className="flex w-full flex-col text-left"
      >
        <div className="flex items-baseline gap-2">
          <span
            className={`text-[10.5px] font-semibold ${alarmToneTextClass(tone)}`}
          >
            {isDone ? "완료" : alarm.severity === "CRITICAL" ? "경보" : "주의"}
          </span>
          <div className="flex-1" />
          <span className="font-mono text-[11px] text-dp-muted">
            {formatAlarmTimestamp(alarm.occurredAt)}
          </span>
        </div>
        <p
          className={`mt-2 text-[13.5px] leading-[1.45] ${isDone ? "font-normal text-dp-sub" : "font-semibold text-dp-ink"}`}
        >
          {alarm.message}
        </p>
        <p className="mt-1.5 text-[11.5px] leading-[1.4] text-dp-muted">
          {location} · {ALARM_STATUS_LABELS[alarm.status]}
        </p>
      </button>

      {isTopUnacknowledged && (
        <div className="mt-3 flex gap-2">
          {canWrite && (
            <button
              type="button"
              disabled={actionBusy}
              onClick={onAcknowledge}
              className="min-h-11 flex-1 rounded-[8px] border border-dp-line-strong text-[12.5px] font-semibold text-dp-body disabled:opacity-40"
            >
              확인
            </button>
          )}
          <Link
            href={`/farms/${farmId}/control`}
            className="flex min-h-11 flex-1 items-center justify-center rounded-[8px] bg-dp-green text-[12.5px] font-semibold text-dp-on-green"
          >
            제어 이동
          </Link>
        </div>
      )}
    </div>
  );
}
