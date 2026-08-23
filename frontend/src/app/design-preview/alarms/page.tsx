"use client";

import Link from "next/link";
import { useState } from "react";
import {
  PageTitle,
  Screen,
  ScreenBody,
  ScreenMain,
  SubNav,
  TopBar,
} from "@/components/design-preview/chrome";
import type { Severity } from "@/components/design-preview/mock";
import { ALARM_STATS, ALARM_TOTAL, ALARMS } from "@/components/design-preview/mock";
import { Card, CardTitle, Chip, GhostButton } from "@/components/design-preview/ui";

// 시안 2c — 알람 · 현황·이력 통합 목록.
// 좌측 목록에서 행을 고르면 우측 처리 패널(상세 + 처리 이력)이 그 알람으로 바뀐다.
// 필터 칩의 건수는 시안의 예시값(전체 12)이고, 실제로 그려지는 행은 목업 8건이다.

type Filter = "all" | Severity;

const FILTERS: { key: Filter; label: string; tone: "neutral" | "critical" | "warning" }[] = [
  { key: "all", label: `전체 ${ALARM_TOTAL}`, tone: "neutral" },
  { key: "critical", label: "경보 3", tone: "critical" },
  { key: "warning", label: "주의 5", tone: "warning" },
  { key: "done", label: "완료 4", tone: "neutral" },
];

const ROW_COLS = "grid-cols-[78px_1fr_128px_96px_82px]";

export default function DesignPreviewAlarmsPage() {
  const [filter, setFilter] = useState<Filter>("all");
  const [selectedId, setSelectedId] = useState(ALARMS[0].id);

  const visible = filter === "all" ? ALARMS : ALARMS.filter((a) => a.severity === filter);
  // 필터 때문에 선택 행이 목록에서 사라져도 우측 패널은 마지막 선택을 유지한다.
  const selected = ALARMS.find((a) => a.id === selectedId) ?? ALARMS[0];

  return (
    <Screen>
      <TopBar farmLabel="전체 농장 4곳" status={["미확인 3"]} compact />

      <ScreenBody>
        <SubNav section="알람">
          <div className="mt-4 rounded-lg bg-dp-inset p-3">
            <div className="mb-2 font-mono text-[11px] leading-none font-semibold text-dp-muted">최근 7일</div>
            {ALARM_STATS.map((stat) => (
              <div
                key={stat.label}
                className="flex justify-between py-1.5 text-[12px] leading-none font-medium text-dp-body"
              >
                <span>{stat.label}</span>
                <b
                  className={
                    stat.tone === "critical"
                      ? "text-dp-red-ink"
                      : stat.tone === "warning"
                        ? "text-dp-amber-ink"
                        : "text-dp-ink"
                  }
                >
                  {stat.value}
                </b>
              </div>
            ))}
          </div>
        </SubNav>

        <ScreenMain>
          <PageTitle title="알람 현황">
            <div className="flex-1" />
            <div className="flex gap-1.5">
              {FILTERS.map((f) => (
                <Chip
                  key={f.key}
                  as="button"
                  active={f.key === filter}
                  tone={f.tone}
                  onClick={() => setFilter(f.key)}
                >
                  {f.label}
                </Chip>
              ))}
            </div>
            <GhostButton size="sm">전체 확인 처리</GhostButton>
          </PageTitle>

          <div className="grid min-h-0 flex-1 grid-cols-[1fr_344px] gap-3">
            <Card className="flex flex-col overflow-hidden">
              <div
                className={`grid ${ROW_COLS} gap-2.5 border-b border-dp-line bg-dp-inset-alt px-4 py-3 font-mono text-[10.5px] leading-none font-semibold tracking-[0.04em] text-dp-muted`}
              >
                <span>등급</span>
                <span>내용</span>
                <span>위치</span>
                <span>발생</span>
                <span>상태</span>
              </div>

              <div className="min-h-0 flex-1 overflow-y-auto">
                {visible.map((alarm) => (
                  <AlarmRow
                    key={alarm.id}
                    alarm={alarm}
                    selected={alarm.id === selected.id}
                    onSelect={() => setSelectedId(alarm.id)}
                  />
                ))}
              </div>

              <div className="flex items-center border-t border-dp-line px-4 py-3 text-[11.5px] leading-none text-dp-muted">
                <span>
                  총 {ALARM_TOTAL}건 · {visible.length}건 표시
                </span>
                <div className="flex-1" />
                <span className="font-semibold text-dp-green">더 보기</span>
              </div>
            </Card>

            <div className="flex min-h-0 flex-col gap-3">
              <AlarmDetailCard alarm={selected} />
              <AlarmHistoryCard alarm={selected} />
            </div>
          </div>
        </ScreenMain>
      </ScreenBody>
    </Screen>
  );
}

function AlarmRow({
  alarm,
  selected,
  onSelect,
}: {
  alarm: (typeof ALARMS)[number];
  selected: boolean;
  onSelect: () => void;
}) {
  const isDone = alarm.severity === "done";
  const severityColor =
    alarm.severity === "critical"
      ? "text-dp-red-ink"
      : alarm.severity === "warning"
        ? "text-dp-amber-deep"
        : "text-dp-muted";
  const statusColor =
    alarm.severity === "critical"
      ? "text-dp-red-ink"
      : alarm.severity === "warning"
        ? "text-dp-amber-deep"
        : "text-dp-green";

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-current={selected ? "true" : undefined}
      className={`grid w-full ${ROW_COLS} gap-2.5 border-b border-dp-line px-4 py-3.5 text-left transition-colors ${
        selected
          ? "border-l-[3px] border-l-dp-red bg-dp-red-tint pl-[13px]"
          : "hover:bg-dp-inset"
      }`}
    >
      <span className={`text-[11px] leading-none font-semibold ${severityColor}`}>{alarm.severityLabel}</span>
      <span
        className={`text-[12.5px] leading-[1.4] ${isDone ? "font-normal text-dp-body" : "font-semibold text-dp-ink"}`}
      >
        {alarm.title}
      </span>
      <span className={`text-[12px] leading-[1.4] font-medium ${isDone ? "text-dp-muted" : "text-dp-body"}`}>
        {alarm.location}
      </span>
      <span className="font-mono text-[11.5px] leading-[1.4] font-medium text-dp-muted">{alarm.time}</span>
      <span className={`text-[11px] leading-none font-semibold ${statusColor}`}>{alarm.status}</span>
    </button>
  );
}

function AlarmDetailCard({ alarm }: { alarm: (typeof ALARMS)[number] }) {
  return (
    <Card className="px-4 py-3.5">
      <div className="mb-3 flex items-baseline justify-between gap-2">
        <CardTitle>{alarm.title}</CardTitle>
        <span
          className={`flex-none font-mono text-[10.5px] leading-none font-semibold ${
            alarm.severity === "critical"
              ? "text-dp-red-ink"
              : alarm.severity === "warning"
                ? "text-dp-amber-deep"
                : "text-dp-green"
          }`}
        >
          {alarm.severityLabel}
        </span>
      </div>

      <dl className="flex flex-col gap-[7px] text-[12px] leading-[1.5] text-dp-body">
        <Field label="위치">{alarm.location}</Field>
        <Field label="규칙">{alarm.detail.rule}</Field>
        <Field label="현재">
          <span
            className={alarm.severity === "done" ? "font-semibold text-dp-green" : "font-semibold text-dp-red-ink"}
          >
            {alarm.detail.current}
          </span>
        </Field>
        <Field label="발생">{alarm.detail.occurred}</Field>
      </dl>

      <p className="mt-3 rounded-lg bg-dp-inset px-3 py-3 text-[11.5px] leading-[1.6] text-dp-body">
        {alarm.detail.cause}
      </p>

      <div className="mt-3 flex gap-2">
        <span className="flex-1 rounded-[7px] border border-dp-line-strong py-2.5 text-center text-[12.5px] leading-none font-semibold text-dp-body">
          확인
        </span>
        <Link
          href="/design-preview/control"
          className="flex-1 rounded-[7px] bg-dp-green py-2.5 text-center text-[12.5px] leading-none font-semibold text-dp-on-green"
        >
          제어 화면 이동
        </Link>
      </div>
    </Card>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex gap-2.5">
      <dt className="w-14 flex-none text-dp-muted">{label}</dt>
      <dd className="min-w-0">{children}</dd>
    </div>
  );
}

function AlarmHistoryCard({ alarm }: { alarm: (typeof ALARMS)[number] }) {
  return (
    <Card className="flex min-h-0 flex-1 flex-col overflow-hidden px-4 py-3.5">
      <div className="mb-2.5">
        <CardTitle>처리 이력</CardTitle>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">
        {alarm.history.map((entry, i) => (
          <div
            key={`${entry.time}-${i}`}
            className={`flex gap-2.5 py-2 ${i < alarm.history.length - 1 ? "border-b border-dp-line" : ""}`}
          >
            <span className="w-[42px] flex-none font-mono text-[11px] leading-[1.4] font-medium text-dp-muted">
              {entry.time}
            </span>
            <span className="flex-1 text-[12px] leading-[1.4] font-medium text-dp-ink">{entry.text}</span>
          </div>
        ))}
      </div>
      <div className="mt-2.5 rounded-[7px] border border-dp-line-strong py-2.5 text-center text-[12.5px] leading-none font-semibold text-dp-body">
        메모 남기기
      </div>
    </Card>
  );
}
