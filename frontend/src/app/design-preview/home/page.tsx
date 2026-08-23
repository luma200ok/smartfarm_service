"use client";

import Link from "next/link";
import { useState } from "react";
import { AlarmBadge, ChatbotPill, Screen, TopBar } from "@/components/design-preview/chrome";
import {
  ALL_FARMS_LABEL,
  BRIEFING_PILLS,
  CROSS_FARM_NOTE,
  CROSS_FARM_SERIES,
  FARM_CARDS,
  FARM_SORT_KEYS,
  HOME_SHORTCUTS,
  HOME_WEATHER_NOTE,
  NOW_LABEL,
  RACK_CELLS,
  RACK_COLUMNS,
  RACK_LEGEND,
  RACK_ROWS,
  RACK_SELECTED,
  RACK_SELECTED_SUMMARY,
  TODO_ITEMS,
  UNREAD_ALARMS,
} from "@/components/design-preview/mock";
import {
  AxisLabels,
  CardTitle,
  Chip,
  GRID_25,
  HomeCard,
  LegendKey,
  LineChart,
  MiniBars,
  NoteBlock,
  Pill,
  RackGrid,
  RackLegend,
  StatusBadge,
} from "@/components/design-preview/ui";

// 화면 01 — 통합 대시보드(홈). 핸드오프 `Screens › 01`.
// 목적: 4개 농장을 나란히 비교해 어디부터 볼지 정하고, 선택한 농장의 랙 상태로 내려간다.
// 농장 카드 클릭은 페이지 이동 없이 하단 3열(도면·그래프·할 일)의 대상만 바꾼다.
export default function DesignPreviewHomePage() {
  const [selected, setSelected] = useState(0);
  const [sortKey, setSortKey] = useState(FARM_SORT_KEYS[0]);
  const farm = FARM_CARDS[selected];

  // 상태순 = 경보 → 주의 → 정상, 이름순 = 가나다순 (핸드오프 Interactions › 홈)
  const order = { critical: 0, warning: 1, done: 2 } as const;
  const cards = FARM_CARDS.map((f, i) => ({ ...f, index: i })).sort((a, b) =>
    sortKey === "이름순" ? a.name.localeCompare(b.name, "ko") : order[a.badgeTone] - order[b.badgeTone],
  );

  return (
    <Screen>
      <TopBar
        farmLabel={ALL_FARMS_LABEL}
        status={[NOW_LABEL, <AlarmBadge key="alarm" count={UNREAD_ALARMS} />, <ChatbotPill key="chat" />]}
      />

      <main className="flex flex-col gap-4 px-4 py-4 min-[768px]:px-5 min-[1440px]:px-6 min-[1440px]:py-5 min-[1920px]:px-[30px] min-[1920px]:py-[26px]">
        {/* 1층 — 브리핑 헤더 행 */}
        <div className="flex flex-wrap items-center gap-3.5">
          <h1 className="text-[19px] leading-[1.2] font-bold text-dp-ink">오늘 아침 브리핑</h1>
          {BRIEFING_PILLS.map((pill) => (
            <Pill key={pill.label} label={pill.label} alert={pill.alert} />
          ))}
          <div className="hidden flex-1 min-[768px]:block" />
          <div className="flex gap-1.5">
            {FARM_SORT_KEYS.map((key) => (
              <Chip key={key} as="button" active={key === sortKey} onClick={() => setSortKey(key)}>
                {key}
              </Chip>
            ))}
            <Chip>위젯 편집</Chip>
          </div>
        </div>

        {/* 2층 — 농장 카드 4열 (태블릿 2열) */}
        <div className="grid grid-cols-1 gap-3.5 min-[640px]:grid-cols-2 min-[1440px]:grid-cols-4">
          {cards.map((card) => (
            <button
              key={card.name}
              type="button"
              onClick={() => setSelected(card.index)}
              aria-pressed={card.index === selected}
              className={`flex flex-col gap-3.5 rounded-[11px] bg-dp-surface px-[17px] py-4 text-left transition-colors ${
                card.index === selected ? "border-[1.5px] border-dp-green" : "border border-dp-line-2"
              }`}
            >
              <div className="flex items-start gap-2.5">
                <div className="min-w-0 flex-1">
                  <div className="truncate text-[15px] leading-[1.3] font-bold text-dp-ink">{card.name}</div>
                  <div className="mt-1.5 truncate text-[11.5px] leading-none text-dp-muted">{card.meta}</div>
                </div>
                <StatusBadge label={card.badge} tone={card.badgeTone} />
              </div>

              <div className="grid grid-cols-3 gap-2">
                {card.metrics.map((m) => (
                  <div key={m.label}>
                    <div className="text-[10.5px] leading-none font-medium text-dp-muted">{m.label}</div>
                    <div
                      className={`mt-1.5 text-[17px] leading-none font-bold ${
                        m.tone === "critical"
                          ? "text-dp-red-ink"
                          : m.tone === "warning"
                            ? "text-dp-amber-ink"
                            : "text-dp-ink"
                      }`}
                    >
                      {m.value}
                    </div>
                  </div>
                ))}
              </div>

              <MiniBars bars={card.bars} />

              <div
                className={`rounded-[7px] px-[11px] py-2.5 text-[11.5px] leading-[1.5] font-medium ${
                  card.summaryTone === "critical"
                    ? "bg-dp-red-tint text-dp-red-deep"
                    : card.summaryTone === "warning"
                      ? "bg-dp-amber-tint text-dp-amber-sub"
                      : "bg-dp-inset text-dp-sub"
                }`}
              >
                {card.summary}
              </div>
            </button>
          ))}
        </div>

        {/* 3층 — 도면 / 농장 간 그래프 / 할 일·바로가기 */}
        <div className="grid gap-3.5 min-[1440px]:grid-cols-[1.35fr_1fr_340px] min-[1920px]:grid-cols-[1.35fr_1fr_400px]">
          <RackCard farmName={farm.name} />
          <CrossFarmCard />
          <div className="flex flex-col gap-3">
            <TodoCard />
            <ShortcutCard />
          </div>
        </div>
      </main>
    </Screen>
  );
}

/* ── 랙 배치도 ────────────────────────────────────────────────────────── */

function RackCard({ farmName }: { farmName: string }) {
  return (
    <HomeCard className="flex flex-col px-[18px] py-4">
      <div className="mb-3 flex flex-wrap items-baseline gap-2.5">
        <CardTitle size="lg">{farmName} · A동 랙 배치</CardTitle>
        <span className="text-[11.5px] leading-none text-dp-muted">상단에서 선택한 농장</span>
        <div className="flex-1" />
        <span className="text-[11.5px] leading-none font-semibold text-dp-green">전체 도면</span>
      </div>

      {/* 좁은 폭에서는 셀을 줄이지 않고 가로 스크롤 (최소 변 14px).
          카드가 옆 카드 높이에 끌려 늘어나면 셀이 세로로 길어지므로 상한을 둔다. */}
      <div className="flex max-h-[280px] min-h-[180px] flex-1 gap-3 overflow-x-auto">
        <div className="flex flex-none flex-col justify-around font-mono text-[10.5px] leading-none font-semibold text-dp-muted">
          {RACK_ROWS.map((row) => (
            <span key={row}>{row}</span>
          ))}
        </div>
        <RackGrid cells={RACK_CELLS} columns={RACK_COLUMNS} selected={RACK_SELECTED} />
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-3.5 border-t border-dp-line pt-[11px]">
        <RackLegend items={RACK_LEGEND} />
        <div className="flex-1" />
        <span className="text-[11.5px] leading-none font-semibold text-dp-red-ink">{RACK_SELECTED_SUMMARY}</span>
      </div>
    </HomeCard>
  );
}

/* ── 농장 간 온도·습도 ────────────────────────────────────────────────── */

function CrossFarmCard() {
  return (
    <HomeCard className="flex flex-col px-[18px] py-4">
      <div className="mb-3 flex flex-wrap items-baseline gap-2.5">
        <CardTitle size="lg">농장 간 온도 · 습도</CardTitle>
        <span className="text-[11.5px] leading-none text-dp-muted">24시간</span>
        <div className="flex-1" />
        {CROSS_FARM_SERIES.map((s) => (
          <LegendKey key={s.key} tone={s.tone} label={s.label} />
        ))}
      </div>

      <LineChart
        className={`min-h-[150px] flex-1 ${GRID_25}`}
        viewBox="0 0 400 140"
        lines={CROSS_FARM_SERIES.map((s) => ({
          points: s.points,
          tone: s.tone,
          width: s.width,
          dashed: s.dashed,
        }))}
      />
      <AxisLabels className="mt-2" labels={["00", "06", "12", "18", "24"]} />

      <NoteBlock className="mt-3">{CROSS_FARM_NOTE}</NoteBlock>
    </HomeCard>
  );
}

/* ── 할 일 / 바로가기 ─────────────────────────────────────────────────── */

const TODO_BAR = {
  critical: "bg-dp-red",
  warning: "bg-dp-amber",
  plain: "bg-dp-disabled",
} as const;

function TodoCard() {
  return (
    <HomeCard className="flex flex-col px-4 py-[15px]">
      <div className="mb-2.5 flex items-baseline">
        <CardTitle>할 일</CardTitle>
        <div className="flex-1" />
        <span className="text-[11.5px] leading-none text-dp-muted">{TODO_ITEMS.length}건</span>
      </div>
      {TODO_ITEMS.map((item, i) => (
        <div
          key={item.title}
          className={`flex gap-2.5 py-2 ${i < TODO_ITEMS.length - 1 ? "border-b border-dp-line-row" : ""}`}
        >
          <span className={`w-[5px] flex-none rounded-[3px] ${TODO_BAR[item.tone]}`} aria-hidden />
          <span className="min-w-0 flex-1">
            <span
              className={`block text-[12.5px] leading-[1.4] text-dp-ink ${
                item.tone === "critical" ? "font-semibold" : "font-medium"
              }`}
            >
              {item.title}
            </span>
            <span className="mt-1 block text-[11px] leading-none text-dp-muted">{item.meta}</span>
          </span>
        </div>
      ))}
    </HomeCard>
  );
}

function ShortcutCard() {
  return (
    <HomeCard className="flex flex-1 flex-col px-4 py-[15px]">
      <div className="mb-3">
        <CardTitle>바로가기</CardTitle>
      </div>
      <div className="grid grid-cols-2 gap-2">
        {HOME_SHORTCUTS.map((label, i) => (
          <Link
            key={label}
            href={i === 0 ? "/design-preview/services" : "/design-preview/data"}
            className={`rounded-[7px] py-2.5 text-center text-[12px] leading-none font-semibold ${
              i === 0
                ? "border border-dp-green-line bg-dp-green-tint text-dp-green-ink"
                : "border border-dp-line bg-dp-inset text-dp-body"
            }`}
          >
            {label}
          </Link>
        ))}
      </div>
      <div className="flex-1" />
      <div className="mt-[11px] border-t border-dp-line pt-[11px] text-[11.5px] leading-[1.6] text-dp-muted">
        {HOME_WEATHER_NOTE[0]}
        <br />
        {HOME_WEATHER_NOTE[1]}
      </div>
    </HomeCard>
  );
}
