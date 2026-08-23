"use client";

import { useState } from "react";
import { PageTitle, Screen, ScreenBody, TopBar } from "@/components/design-preview/chrome";
import {
  DATA_ANNOTATION,
  DATA_CHART_SUB,
  DATA_CHART_TITLE,
  DATA_DEFAULT_METRICS,
  DATA_METRIC_LIMIT,
  DATA_METRICS,
  DATA_RANGES,
  DATA_X_LABELS,
  DATA_X_LABELS_NARROW,
  FLOOR_COMPARISON,
  REPORTS,
  SAVED_ANALYSES,
  UNREAD_ALARMS,
} from "@/components/design-preview/mock";
import type { Line } from "@/components/design-preview/ui";
import {
  AxisLabels,
  Card,
  CardTitle,
  Chip,
  GRID_20,
  GhostButton,
  LegendKey,
  LineChart,
  PrimaryButton,
} from "@/components/design-preview/ui";

// 화면 03 — 그래프 분석. 핸드오프 `Screens › 03` + `Interactions › 데이터`.
// 기간 세그먼트·항목 칩·대상 드롭다운의 변경은 즉시 그래프에 반영된다.
// 항목 칩은 다중 선택이고 최대 4개(초과 시 안내).
// 시계열은 프로토타입 SVG 좌표를 그대로 쓰는 목업이라 기간을 바꿔도 데이터는 동일하다.

const CHIP_TONE: Record<string, string> = {
  green: "border-dp-green bg-dp-green-tint-2 text-dp-green-ink",
  blue: "border-dp-blue bg-dp-blue-tint text-dp-blue-ink",
  amber: "border-dp-amber bg-dp-amber-tint-2 text-dp-amber-sub",
  neutral: "border-dp-green bg-dp-green-tint-2 text-dp-green-ink",
};

export default function DesignPreviewDataPage() {
  const [range, setRange] = useState(DATA_RANGES[1]);
  const [selected, setSelected] = useState<string[]>(DATA_DEFAULT_METRICS);
  const [limitNotice, setLimitNotice] = useState(false);

  const shown = DATA_METRICS.filter((m) => m.series && selected.includes(m.key));
  const lines: Line[] = shown.map((m) => ({
    points: m.series as string,
    tone: m.tone,
    width: m.width,
    dashed: m.dashed,
  }));

  function toggleMetric(key: string) {
    setSelected((prev) => {
      if (prev.includes(key)) {
        setLimitNotice(false);
        return prev.filter((k) => k !== key);
      }
      if (prev.length >= DATA_METRIC_LIMIT) {
        setLimitNotice(true);
        return prev;
      }
      setLimitNotice(false);
      return [...prev, key];
    });
  }

  return (
    <Screen>
      <TopBar status={[`알람 ${UNREAD_ALARMS}`]} />

      <ScreenBody
        section="data"
        aside={
          <div className="mt-3.5">
            <div className="px-2 pb-2 font-mono text-[10.5px] leading-none font-semibold tracking-[0.06em] text-dp-muted">
              저장한 분석
            </div>
            {SAVED_ANALYSES.map((name) => (
              <div key={name} className="px-3 py-2.5 text-[12px] leading-none text-dp-body">
                {name}
              </div>
            ))}
          </div>
        }
      >
        <PageTitle title="그래프 분석">
          <div className="hidden flex-1 min-[768px]:block" />
          <div className="flex gap-1.5 overflow-x-auto">
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
                    : "border-dp-line-strong bg-dp-surface font-medium text-dp-sub"
                }`}
              >
                {metric.label}
              </button>
            );
          })}
          {limitNotice ? (
            <span className="text-[11.5px] leading-none font-medium text-dp-amber-deep">
              항목은 최대 {DATA_METRIC_LIMIT}개까지 선택할 수 있습니다
            </span>
          ) : null}
          <div className="hidden flex-1 min-[1440px]:block" />
          <span className="text-[11.5px] leading-none font-medium text-dp-muted">대상</span>
          <span className="rounded-md border border-dp-line-strong bg-dp-surface px-3 py-1.5 text-[11.5px] leading-none font-medium text-dp-body">
            A동 전체 ▾
          </span>
        </div>

        <Card className="flex flex-col px-[18px] py-4">
          <div className="mb-3 flex flex-wrap items-baseline gap-3">
            <CardTitle size="lg">{DATA_CHART_TITLE}</CardTitle>
            <span className="text-[11.5px] leading-none text-dp-muted">{DATA_CHART_SUB}</span>
            <div className="flex-1" />
            {shown.map((m) => (
              <LegendKey key={m.key} tone={m.tone} label={m.label} />
            ))}
          </div>

          <LineChart className={`min-h-[280px] flex-1 min-[1440px]:min-h-[360px] ${GRID_20}`} viewBox="0 0 700 200" lines={lines}>
            {/* 08.21 EC 경보 구간 — 클릭하면 해당 알람 상세로 (핸드오프 Interactions) */}
            <div className="absolute inset-y-0 left-[58.5%] w-[6%] bg-dp-red opacity-[0.07]" />
            <div className="absolute top-1.5 left-[58.5%] rounded-[5px] bg-dp-ink px-2 py-1 text-[10.5px] leading-none font-semibold whitespace-nowrap text-dp-surface">
              {DATA_ANNOTATION}
            </div>
          </LineChart>

          {/* 폭이 좁으면 축 라벨 개수를 줄인다 */}
          <AxisLabels className="mt-2 min-[1440px]:hidden" labels={DATA_X_LABELS_NARROW} />
          <AxisLabels className="mt-2 hidden min-[1440px]:flex" labels={DATA_X_LABELS} />
        </Card>

        <div className="grid gap-3 min-[1440px]:grid-cols-[1.4fr_1fr]">
          <FloorComparisonCard />
          <ReportsCard />
        </div>
      </ScreenBody>
    </Screen>
  );
}

/* 층별 비교표 — 좁은 폭에서는 PPFD → 습도 순으로 열을 숨긴다 */
const FLOOR_COLS =
  "grid grid-cols-[52px_1fr_66px] min-[640px]:grid-cols-[52px_repeat(2,1fr)_66px] min-[1440px]:grid-cols-[52px_repeat(4,1fr)_66px]";

function FloorComparisonCard() {
  return (
    <Card className="flex flex-col px-4 py-3.5">
      <div className="mb-2.5">
        <CardTitle>층별 평균 비교 · 최근 7일</CardTitle>
      </div>
      <div
        className={`${FLOOR_COLS} gap-2 border-b border-dp-line pb-2 font-mono text-[10.5px] leading-none font-semibold text-dp-muted`}
      >
        <span />
        <span>온도</span>
        <span className="hidden min-[640px]:block">습도</span>
        <span className="hidden min-[1440px]:block">EC</span>
        <span className="hidden min-[1440px]:block">PPFD</span>
        <span>편차</span>
      </div>
      {FLOOR_COMPARISON.map((row, i) => (
        <div
          key={row.floor}
          className={`${FLOOR_COLS} gap-2 py-[7px] text-[12px] leading-none font-medium text-dp-ink ${
            i < FLOOR_COMPARISON.length - 1 ? "border-b border-dp-line-row" : ""
          }`}
        >
          <span className="text-dp-muted">{row.floor}</span>
          <span>{row.temp}</span>
          <span className="hidden min-[640px]:block">{row.humidity}</span>
          <span className="hidden min-[1440px]:block">{row.ec}</span>
          <span className="hidden min-[1440px]:block">{row.ppfd}</span>
          <span
            className={`font-semibold ${
              row.tone === "critical"
                ? "text-dp-red-ink"
                : row.tone === "warning"
                  ? "text-dp-amber-ink"
                  : "text-dp-green"
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
    <Card className="flex flex-col px-4 py-3.5">
      <div className="flex items-baseline">
        <CardTitle>리포트</CardTitle>
        <div className="flex-1" />
        <span className="text-[11.5px] leading-none font-semibold text-dp-green">전체 보기</span>
      </div>
      <div className="mt-2.5 flex flex-col">
        {REPORTS.map((report) => (
          <div key={report.title} className="flex items-center gap-2.5 border-b border-dp-line-row py-2">
            <span className="min-w-0 flex-1 truncate text-[12.5px] leading-none font-medium text-dp-ink">
              {report.title}
            </span>
            <span className="flex-none text-[11px] leading-none font-semibold text-dp-muted">{report.format}</span>
          </div>
        ))}
      </div>
      <div className="flex-1" />
      <div className="mt-2.5 rounded-[7px] border border-dp-line-strong py-2.5 text-center text-[12.5px] leading-none font-semibold text-dp-body">
        리포트 예약 설정
      </div>
    </Card>
  );
}
