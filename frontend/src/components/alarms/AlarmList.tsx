"use client";

import { ALARM_STATUS_LABELS } from "@/constants";
import { Card } from "@/components/monitoring/ui";
import { alarmDisplayTone, alarmToneTextClass, describeAlarmScope, formatAlarmTimestamp } from "@/lib/alarmDisplay";
import type { LocationMaps } from "@/lib/zoneTree";
import type { AlarmEventResponse } from "@/types";
import type { AlarmFilterKey } from "./filters";

const ROW_COLS =
  "grid grid-cols-[64px_1fr_72px] gap-2.5 min-[768px]:grid-cols-[78px_1fr_128px_82px] min-[1440px]:grid-cols-[78px_1fr_128px_96px_82px]";

interface AlarmListProps {
  filter: AlarmFilterKey;
  items: AlarmEventResponse[];
  totalElements: number;
  loading: boolean;
  loadingMore: boolean;
  error: string | null;
  selectedId: number | null;
  onSelect: (id: number) => void;
  onLoadMore: () => void;
  onShowAll: () => void;
  onRefresh: () => void;
  locationMaps: LocationMaps | null;
  farmName: string | null;
}

// 알람 목록 카드(시안 04 · 이슈 #136) — grid-template-columns 78px/1fr/128px/96px/82px(등급/내용/
// 위치/발생/상태). 미확인 행은 배경+좌측 바로 강조하고, 처리 완료 행은 내용을 dim 처리한다.
export default function AlarmList({
  filter,
  items,
  totalElements,
  loading,
  loadingMore,
  error,
  selectedId,
  onSelect,
  onLoadMore,
  onShowAll,
  onRefresh,
  locationMaps,
  farmName,
}: AlarmListProps) {
  const canLoadMore = items.length < totalElements;

  return (
    <Card className="flex flex-col overflow-hidden">
      <div
        className={`${ROW_COLS} border-b border-dp-line bg-dp-inset-alt px-4 py-3 font-mono text-[10.5px] leading-none font-semibold tracking-[0.04em] text-dp-muted`}
      >
        <span>등급</span>
        <span>내용</span>
        <span className="hidden min-[768px]:block">위치</span>
        <span className="hidden min-[1440px]:block">발생</span>
        <span>상태</span>
      </div>

      {error && <p className="px-4 py-4 text-[12.5px] text-dp-red-ink">{error}</p>}

      {!error && loading && <p className="px-4 py-6 text-[12.5px] text-dp-muted">불러오는 중...</p>}

      {!error && !loading && items.length === 0 && (
        <div className="flex flex-col items-center gap-3 px-4 py-10 text-center">
          <p className="text-[12.5px] text-dp-sub">
            {filter === "ALL" ? "등록된 알람이 없습니다." : "이 조건에 해당하는 알람이 없습니다."}
          </p>
          <button
            type="button"
            onClick={filter === "ALL" ? onRefresh : onShowAll}
            className="rounded-[7px] border border-dp-line-strong px-3.5 py-2 text-[12.5px] font-semibold text-dp-body hover:bg-dp-inset"
          >
            {filter === "ALL" ? "새로고침" : "전체 보기"}
          </button>
        </div>
      )}

      {!error && !loading && items.length > 0 && (
        <div>
          {items.map((alarm) => (
            <AlarmRow
              key={alarm.id}
              alarm={alarm}
              selected={alarm.id === selectedId}
              location={describeAlarmScope(alarm, locationMaps, farmName)}
              onSelect={() => onSelect(alarm.id)}
            />
          ))}
        </div>
      )}

      {!error && (
        <div className="flex items-center border-t border-dp-line px-4 py-3 text-[11.5px] leading-none text-dp-muted">
          <span>
            {totalElements}건 중 {items.length}건 표시
          </span>
          <div className="flex-1" />
          {canLoadMore && (
            <button
              type="button"
              onClick={onLoadMore}
              disabled={loadingMore}
              className="font-semibold text-dp-green disabled:opacity-50"
            >
              {loadingMore ? "불러오는 중..." : "더 보기"}
            </button>
          )}
        </div>
      )}
    </Card>
  );
}

function AlarmRow({
  alarm,
  selected,
  location,
  onSelect,
}: {
  alarm: AlarmEventResponse;
  selected: boolean;
  location: string;
  onSelect: () => void;
}) {
  const tone = alarmDisplayTone(alarm.severity, alarm.status);
  const isDone = tone === "done";
  const unread = alarm.status === "UNACKNOWLEDGED";

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-current={selected ? "true" : undefined}
      className={`w-full ${ROW_COLS} border-b border-dp-line-row px-4 py-3.5 text-left transition-colors ${
        unread ? "border-l-[3px] border-l-dp-red bg-dp-red-tint pl-[13px]" : ""
      } ${selected && !unread ? "bg-dp-inset" : ""} ${!selected && !unread ? "hover:bg-dp-inset" : ""}`}
    >
      <span className={`text-[11px] leading-none font-semibold ${alarmToneTextClass(tone)}`}>
        {isDone ? "완료" : alarm.severity === "CRITICAL" ? "경보" : "주의"}
      </span>
      <span className={`min-w-0 text-[12.5px] leading-[1.4] ${isDone ? "font-normal text-dp-sub" : "font-semibold text-dp-ink"}`}>
        {alarm.message}
      </span>
      <span className={`hidden text-[12px] leading-[1.4] font-medium min-[768px]:block ${isDone ? "text-dp-muted" : "text-dp-body"}`}>
        {location}
      </span>
      <span className="hidden font-mono text-[11.5px] leading-[1.4] font-medium text-dp-muted min-[1440px]:block">
        {formatAlarmTimestamp(alarm.occurredAt)}
      </span>
      <span className={`text-[11px] leading-none font-semibold ${alarmToneTextClass(tone)}`}>
        {ALARM_STATUS_LABELS[alarm.status]}
      </span>
    </button>
  );
}
