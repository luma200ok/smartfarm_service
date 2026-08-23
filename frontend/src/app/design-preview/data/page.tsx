"use client";

import { useState } from "react";
import {
  PageTitle,
  Screen,
  ScreenBody,
  ScreenMain,
  SubNav,
  TopBar,
} from "@/components/design-preview/chrome";
import {
  DATA_ANNOTATION,
  DATA_CHART_SUB,
  DATA_CHART_TITLE,
  DATA_DEFAULT_METRICS,
  DATA_METRICS,
  DATA_RANGES,
  DATA_X_LABELS,
  FLOOR_COMPARISON,
  REPORTS,
  SAVED_ANALYSES,
} from "@/components/design-preview/mock";
import type { Line } from "@/components/design-preview/ui";
import { AxisLabels, Card, CardTitle, Chip, GhostButton, LegendKey, LineChart, PrimaryButton } from "@/components/design-preview/ui";

// 시안 2b — 데이터 · 그래프 분석.
// 기간 탭과 항목 칩은 실제로 눌리고, 항목을 끄면 해당 시계열이 그래프에서 빠진다.
// 시계열은 시안 SVG의 좌표를 그대로 쓰는 목업이라 기간을 바꿔도 데이터는 동일하다.

const CHIP_TONE: Record<string, string> = {
  green: "border-dp-green bg-dp-green-tint-2 text-dp-green-ink",
  blue: "border-dp-blue bg-dp-blue-tint text-dp-blue-ink",
  amber: "border-dp-amber bg-dp-amber-tint text-dp-amber-sub",
  neutral: "border-dp-green bg-dp-green-tint-2 text-dp-green-ink",
};

export default function DesignPreviewDataPage() {
  const [range, setRange] = useState(DATA_RANGES[1]);
  const [selected, setSelected] = useState<string[]>(DATA_DEFAULT_METRICS);

  const lines: Line[] = DATA_METRICS.filter((m) => m.series && selected.includes(m.key)).map((m) => ({
    points: m.series as string,
    tone: m.tone === "neutral" ? "muted" : m.tone,
    width: m.dashed ? 1.6 : 2,
    dashed: m.dashed,
  }));

  function toggleMetric(key: string) {
    setSelected((prev) => (prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key]));
  }

  return (
    <Screen>
      <TopBar status={["알람 3"]} compact />

      <ScreenBody>
        <SubNav section="데이터">
          <div className="mt-3.5 px-2 pb-2 font-mono text-[10.5px] leading-none font-semibold tracking-[0.06em] text-dp-muted">
            저장한 분석
          </div>
          {SAVED_ANALYSES.map((name) => (
            <div key={name} className="px-3 py-2.5 text-[12px] leading-none text-dp-body">
              {name}
            </div>
          ))}
        </SubNav>

        <ScreenMain className="gap-3.5">
          <PageTitle title="그래프 분석">
            <div className="flex-1" />
            <div className="flex gap-1.5">
              {DATA_RANGES.map((r) => (
                <Chip key={r} as="button" active={r === range} onClick={() => setRange(r)}>
                  {r}
                </Chip>
              ))}
            </div>
            <GhostButton size="sm">CSV</GhostButton>
            <PrimaryButton size="sm">분석 저장</PrimaryButton>
          </PageTitle>

          <div className="flex flex-wrap items-center gap-1.5">
            <span className="mr-1 text-[11.5px] leading-none font-medium text-dp-muted">항목</span>
            {DATA_METRICS.map((metric) => {
              const on = selected.includes(metric.key);
              return (
                <button
                  key={metric.key}
                  type="button"
                  aria-pressed={on}
                  onClick={() => toggleMetric(metric.key)}
                  className={`rounded-[20px] border px-3 py-1.5 text-[11.5px] leading-none transition-colors ${
                    on
                      ? `font-semibold ${CHIP_TONE[metric.tone]}`
                      : "border-dp-line-strong bg-dp-surface font-medium text-dp-muted"
                  }`}
                >
                  {metric.label}
                </button>
              );
            })}
            <div className="flex-1" />
            <span className="text-[11.5px] leading-none font-medium text-dp-muted">대상</span>
            <span className="rounded-md border border-dp-line-strong bg-dp-surface px-3 py-1.5 text-[11.5px] leading-none font-medium text-dp-body">
              A동 전체 ▾
            </span>
          </div>

          <Card className="flex min-h-0 flex-1 flex-col px-4.5 py-4">
            <div className="mb-3 flex items-baseline gap-3">
              <CardTitle size="lg">{DATA_CHART_TITLE}</CardTitle>
              <span className="text-[11.5px] leading-none text-dp-muted">{DATA_CHART_SUB}</span>
              <div className="flex-1" />
              {DATA_METRICS.filter((m) => m.series && selected.includes(m.key)).map((m) => (
                <LegendKey key={m.key} tone={m.tone === "neutral" ? "muted" : m.tone} label={m.label} />
              ))}
            </div>

            <LineChart
              className="flex-1 bg-[linear-gradient(to_top,var(--dp-line)_1px,transparent_1px)] bg-[length:100%_20%]"
              viewBox="0 0 700 200"
              lines={lines}
            >
              {/* 08.21 EC 경보 구간 */}
              <div className="absolute inset-y-0 left-[58.5%] w-[6%] bg-dp-red opacity-[0.07]" />
              <div className="absolute top-1.5 left-[58.5%] rounded-[5px] bg-dp-ink px-2 py-1 text-[10.5px] leading-none font-semibold text-dp-surface">
                {DATA_ANNOTATION}
              </div>
            </LineChart>

            <AxisLabels labels={DATA_X_LABELS} />
          </Card>

          <div className="grid h-[184px] flex-none grid-cols-[1.4fr_1fr] gap-3">
            <FloorComparisonCard />
            <ReportsCard />
          </div>
        </ScreenMain>
      </ScreenBody>
    </Screen>
  );
}

const FLOOR_COLS = "grid-cols-[52px_repeat(4,1fr)_66px]";

function FloorComparisonCard() {
  return (
    <Card className="flex flex-col overflow-hidden px-4 py-3.5">
      <div className="mb-2.5">
        <CardTitle>층별 평균 비교 · 최근 7일</CardTitle>
      </div>
      <div
        className={`grid ${FLOOR_COLS} gap-2 border-b border-dp-line pb-2 font-mono text-[10.5px] leading-none font-semibold text-dp-muted`}
      >
        <span />
        <span>온도</span>
        <span>습도</span>
        <span>EC</span>
        <span>PPFD</span>
        <span>편차</span>
      </div>
      {FLOOR_COMPARISON.map((row, i) => (
        <div
          key={row.floor}
          className={`grid ${FLOOR_COLS} gap-2 py-[7px] text-[12px] leading-none font-medium text-dp-ink ${
            i < FLOOR_COMPARISON.length - 1 ? "border-b border-dp-line" : ""
          }`}
        >
          <span className="text-dp-muted">{row.floor}</span>
          <span>{row.temp}</span>
          <span>{row.humidity}</span>
          <span>{row.ec}</span>
          <span>{row.ppfd}</span>
          <span
            className={`font-semibold ${
              row.tone === "critical" ? "text-dp-red-ink" : row.tone === "warning" ? "text-dp-amber-ink" : "text-dp-green"
            }`}
          >
            {row.deviation}
          </span>
        </div>
      ))}
    </Card>
  );
}

function ReportsCard() {
  return (
    <Card className="flex flex-col overflow-hidden px-4 py-3.5">
      <div className="flex items-baseline">
        <CardTitle>리포트</CardTitle>
        <div className="flex-1" />
        <span className="text-[11.5px] leading-none font-semibold text-dp-green">전체 보기</span>
      </div>
      <div className="mt-2.5 flex flex-col">
        {REPORTS.map((report) => (
          <div key={report.title} className="flex items-center gap-2.5 border-b border-dp-line py-2">
            <span className="flex-1 text-[12.5px] leading-none font-medium text-dp-ink">{report.title}</span>
            <span className="text-[11px] leading-none font-semibold text-dp-muted">{report.format}</span>
          </div>
        ))}
      </div>
      <div className="flex-1" />
      <div className="rounded-[7px] border border-dp-line-strong py-2.5 text-center text-[12.5px] leading-none font-semibold text-dp-body">
        리포트 예약 설정
      </div>
    </Card>
  );
}
