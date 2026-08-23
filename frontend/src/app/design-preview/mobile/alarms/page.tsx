"use client";

import { useState } from "react";
import { MobileFrame } from "@/components/design-preview/MobileFrame";
import { M_ALARM_FILTERS, M_ALARMS } from "@/components/design-preview/mock";

// 화면 M3 — 모바일 알람. 핸드오프 `Mobile`.
// 목록이 카드 스택. 좌측 3px 세로 바로 등급을 표시하고,
// 미확인 최상위 알람에만 액션 버튼 2개를 노출한다. 나머지는 탭하면 상세로 이동.

const LEFT_BAR = {
  critical: "border-l-dp-red",
  warning: "border-l-dp-amber",
  done: "border-l-dp-disabled",
} as const;

const SEVERITY_TEXT = {
  critical: "text-dp-red-ink",
  warning: "text-dp-amber-deep",
  done: "text-dp-muted",
} as const;

export default function MobileAlarmsPage() {
  const [filter, setFilter] = useState(0);

  return (
    <MobileFrame
      activeTab="alarm"
      header={
        <div className="px-4.5 py-3.5">
          <div className="flex items-center gap-2.5">
            <div className="text-[14.5px] leading-none font-semibold">알람</div>
            <div className="flex-1" />
            <span className="text-[11.5px] leading-none font-medium text-white/70">전체 확인</span>
          </div>
          <div className="mt-3 flex gap-1.5 overflow-x-auto">
            {M_ALARM_FILTERS.map((label, i) => (
              <button
                key={label}
                type="button"
                aria-pressed={i === filter}
                onClick={() => setFilter(i)}
                className={`rounded-[7px] px-3.5 py-2.5 text-[12px] leading-none whitespace-nowrap ${
                  i === filter
                    ? "bg-white/[0.16] font-semibold text-white"
                    : i === 1
                      ? "font-medium text-[#ff9b90]"
                      : i === 2
                        ? "font-medium text-[#e8c37a]"
                        : "font-normal text-white/55"
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
      }
    >
      {M_ALARMS.map((alarm, i) => (
        <div
          key={alarm.title}
          className={`rounded-[10px] border-l-[3px] bg-dp-surface px-[15px] py-3.5 ${LEFT_BAR[alarm.severity]} ${
            alarm.primary ? "border border-dp-red-line border-l-dp-red" : "border border-dp-line"
          }`}
        >
          <div className="flex items-baseline gap-2">
            <span className={`text-[10.5px] leading-none font-semibold ${SEVERITY_TEXT[alarm.severity]}`}>
              {alarm.severityLabel}
            </span>
            <div className="flex-1" />
            <span className="font-mono text-[11px] leading-none font-medium text-dp-muted">{alarm.time}</span>
          </div>
          <div
            className={`mt-2 text-[13.5px] leading-[1.45] ${
              alarm.severity === "done" ? "font-normal text-dp-sub" : i === 0 ? "font-semibold text-dp-ink" : "font-medium text-dp-ink"
            }`}
          >
            {alarm.title}
          </div>
          <div className="mt-1.5 text-[11.5px] leading-[1.4] text-dp-muted">{alarm.meta}</div>

          {alarm.primary ? (
            <div className="mt-3 flex gap-2">
              <span className="flex-1 rounded-lg border border-dp-line-strong py-3 text-center text-[12.5px] leading-none font-semibold text-dp-body">
                확인
              </span>
              <span className="flex-1 rounded-lg bg-dp-green py-3 text-center text-[12.5px] leading-none font-semibold text-dp-on-green">
                제어 이동
              </span>
            </div>
          ) : null}
        </div>
      ))}
    </MobileFrame>
  );
}
