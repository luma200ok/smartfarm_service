"use client";

import Link from "next/link";
import { useState } from "react";
import { DETAIL_COLS, PageTitle, Screen, ScreenBody, TopBar } from "@/components/design-preview/chrome";
import type { Alarm, Severity } from "@/components/design-preview/mock";
import {
  ALARM_FILTERS,
  ALARM_FOOTER,
  ALARM_STATS,
  ALARMS,
  ALL_FARMS_LABEL,
} from "@/components/design-preview/mock";
import { Card, CardTitle, Chip, GhostButton, NoteBlock, TimelineRow } from "@/components/design-preview/ui";

// 화면 04 — 알람 현황. 핸드오프 `Screens › 04` + `Interactions › 알람`.
// 등급 필터는 단일 선택. 목록 행 클릭은 페이지 이동 없이 우측 상세 패널만 갱신한다.
// `확인` 은 상태를 미확인 → 확인됨으로 바꾸고 좌측 강조를 없앤 뒤 처리 이력에 기록을 남긴다.
//
// 좁은 폭에서는 글자를 줄이지 않고 우선순위가 낮은 열을 숨긴다(발생 시각 → 위치 순).

type Filter = "all" | Severity;

const ROW_COLS =
  "grid grid-cols-[64px_1fr_72px] gap-2.5 min-[768px]:grid-cols-[78px_1fr_128px_82px] min-[1440px]:grid-cols-[78px_1fr_128px_96px_82px]";

export default function DesignPreviewAlarmsPage() {
  const [filter, setFilter] = useState<Filter>("all");
  const [selectedId, setSelectedId] = useState(ALARMS[0].id);
  /** 사용자가 이 화면에서 확인 처리한 알람 */
  const [acked, setAcked] = useState<string[]>([]);

  const visible = filter === "all" ? ALARMS : ALARMS.filter((a) => a.severity === filter);
  // 필터 때문에 선택 행이 목록에서 사라져도 우측 패널은 마지막 선택을 유지한다.
  const selected = ALARMS.find((a) => a.id === selectedId) ?? ALARMS[0];
  const selectedAcked = acked.includes(selected.id);

  const unread = ALARMS.filter((a) => a.status === "미확인" && !acked.includes(a.id)).length;

  return (
    <Screen>
      <TopBar farmLabel={ALL_FARMS_LABEL} status={[`미확인 ${unread}`]} />

      <ScreenBody
        section="alarm"
        aside={
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
        }
      >
        <PageTitle title="알람 현황">
          <div className="hidden flex-1 min-[768px]:block" />
          <div className="flex gap-1.5 overflow-x-auto">
            {ALARM_FILTERS.map((f) => (
              <Chip key={f.key} as="button" active={f.key === filter} tone={f.tone} onClick={() => setFilter(f.key)}>
                {f.label}
              </Chip>
            ))}
          </div>
          <button type="button" onClick={() => setAcked(ALARMS.map((a) => a.id))}>
            <GhostButton size="sm">전체 확인 처리</GhostButton>
          </button>
        </PageTitle>

        <div className={DETAIL_COLS[440]}>
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

            <div>
              {visible.map((alarm) => (
                <AlarmRow
                  key={alarm.id}
                  alarm={alarm}
                  acked={acked.includes(alarm.id)}
                  selected={alarm.id === selected.id}
                  onSelect={() => setSelectedId(alarm.id)}
                />
              ))}
            </div>

            <div className="flex items-center border-t border-dp-line px-4 py-3 text-[11.5px] leading-none text-dp-muted">
              <span>{filter === "all" ? ALARM_FOOTER : `${visible.length}건 표시`}</span>
              <div className="flex-1" />
              <span className="font-semibold text-dp-green">이력 전체 보기</span>
            </div>
          </Card>

          <div className="flex flex-col gap-3">
            <AlarmDetailCard
              alarm={selected}
              acked={selectedAcked}
              onAck={() => setAcked((prev) => (prev.includes(selected.id) ? prev : [...prev, selected.id]))}
            />
            <AlarmHistoryCard alarm={selected} acked={selectedAcked} />
          </div>
        </div>
      </ScreenBody>
    </Screen>
  );
}

function severityColor(severity: Severity) {
  return severity === "critical" ? "text-dp-red-ink" : severity === "warning" ? "text-dp-amber-deep" : "text-dp-green";
}

function AlarmRow({
  alarm,
  selected,
  acked,
  onSelect,
}: {
  alarm: Alarm;
  selected: boolean;
  acked: boolean;
  onSelect: () => void;
}) {
  const isDone = alarm.severity === "done";
  const unread = alarm.status === "미확인" && !acked;
  const status = acked && alarm.status === "미확인" ? "확인됨" : alarm.status;

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-current={selected ? "true" : undefined}
      className={`w-full ${ROW_COLS} border-b border-dp-line-row px-4 py-3.5 text-left transition-colors ${
        unread ? "border-l-[3px] border-l-dp-red bg-dp-red-tint pl-[13px]" : ""
      } ${selected && !unread ? "bg-dp-inset" : ""} ${!selected && !unread ? "hover:bg-dp-inset" : ""}`}
    >
      <span
        className={`text-[11px] leading-none font-semibold ${
          alarm.severity === "done" ? "text-dp-muted" : severityColor(alarm.severity)
        }`}
      >
        {alarm.severityLabel}
      </span>
      <span
        className={`min-w-0 text-[12.5px] leading-[1.4] ${isDone ? "font-normal text-dp-sub" : "font-semibold text-dp-ink"}`}
      >
        {alarm.title}
      </span>
      <span
        className={`hidden text-[12px] leading-[1.4] font-medium min-[768px]:block ${isDone ? "text-dp-muted" : "text-dp-body"}`}
      >
        {alarm.location}
      </span>
      <span className="hidden font-mono text-[11.5px] leading-[1.4] font-medium text-dp-muted min-[1440px]:block">
        {alarm.time}
      </span>
      <span
        className={`text-[11px] leading-none font-semibold ${
          acked && alarm.status === "미확인" ? "text-dp-amber-deep" : severityColor(alarm.severity)
        }`}
      >
        {status}
      </span>
    </button>
  );
}

function AlarmDetailCard({ alarm, acked, onAck }: { alarm: Alarm; acked: boolean; onAck: () => void }) {
  return (
    <Card className="px-4 py-[15px]">
      <div className="mb-3 flex items-baseline justify-between gap-2">
        <CardTitle>{alarm.title}</CardTitle>
        <span className={`flex-none font-mono text-[10.5px] leading-none font-semibold ${severityColor(alarm.severity)}`}>
          {alarm.severityLabel}
        </span>
      </div>

      <dl className="flex flex-col gap-[7px] text-[12px] leading-[1.5] text-dp-body">
        <Field label="위치">{alarm.location}</Field>
        <Field label="규칙">{alarm.detail.rule}</Field>
        <Field label="현재">
          <span className={alarm.severity === "done" ? "font-semibold text-dp-green" : "font-semibold text-dp-red-ink"}>
            {alarm.detail.current}
          </span>
        </Field>
        <Field label="발생">{alarm.detail.occurred}</Field>
      </dl>

      <NoteBlock className="mt-3">{alarm.detail.cause}</NoteBlock>

      <div className="mt-3 flex gap-2">
        <button
          type="button"
          onClick={onAck}
          disabled={acked || alarm.status !== "미확인"}
          className={`flex-1 rounded-[7px] border py-2.5 text-center text-[12.5px] leading-none font-semibold ${
            acked || alarm.status !== "미확인"
              ? "border-dp-line text-dp-faint"
              : "border-dp-line-strong text-dp-body hover:bg-dp-inset"
          }`}
        >
          {acked || alarm.status !== "미확인" ? "확인됨" : "확인"}
        </button>
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

function AlarmHistoryCard({ alarm, acked }: { alarm: Alarm; acked: boolean }) {
  // 확인 처리하면 처리 이력에 사용자·시각이 추가된다(핸드오프 Interactions › 알람)
  const history = acked ? [...alarm.history, { time: "지금", text: "김재현 확인 처리", dim: false }] : alarm.history;

  return (
    <Card className="flex flex-1 flex-col px-4 py-[15px]">
      <div className="mb-2.5">
        <CardTitle>처리 이력</CardTitle>
      </div>
      <div>
        {history.map((entry, i) => (
          <TimelineRow
            key={`${entry.time}-${i}`}
            time={entry.time}
            text={entry.text}
            dim={entry.dim}
            last={i === history.length - 1}
          />
        ))}
      </div>

      {alarm.related ? <NoteBlock className="mt-3.5">{alarm.related}</NoteBlock> : null}

      <div className="flex-1" />
      <div className="mt-2.5 rounded-[7px] border border-dp-line-strong py-2.5 text-center text-[12.5px] leading-none font-semibold text-dp-body">
        메모 남기기
      </div>
    </Card>
  );
}
