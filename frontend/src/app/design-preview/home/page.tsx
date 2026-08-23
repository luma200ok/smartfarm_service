import Link from "next/link";
import { AlarmBadge, Screen, ScreenBody, TopBar } from "@/components/design-preview/chrome";
import {
  ACTIVE_FARM,
  EC_SERIES,
  EC_TARGET_SERIES,
  FARMS,
  HOME_BANNER,
  HOME_KPIS,
  HOME_SHORTCUTS,
  HOME_WEATHER_NOTE,
  HUMIDITY_SERIES,
  RACK_CELLS,
  RACK_COLUMNS,
  RACK_LEGEND,
  RACK_ROWS,
  RACK_SELECTED,
  SCHEDULE,
  SELECTED_CELL_DETAIL,
  TEMP_SERIES,
} from "@/components/design-preview/mock";
import {
  AxisLabels,
  Card,
  CardTitle,
  LegendKey,
  LineChart,
  RackGrid,
  RackLegend,
  StatTile,
  StatusDot,
} from "@/components/design-preview/ui";

// 시안 1b — 홈 · PC 1440. 랙 도면형: 왼쪽 농장 목록 → 가운데 도면 → 오른쪽 상세.
// 값 하나하나보다 "어느 농장 어느 층이 목표를 벗어났는지"를 먼저 보여주는 게 이 화면의 목적이라
// 상단 배너 알람 → 요약 타일 → 도면 순으로 시선이 내려가게 배치했다.
export default function DesignPreviewHomePage() {
  return (
    <Screen minWidth={1360}>
      <TopBar
        height={56}
        status={["통신 정상 · 8초 전", <AlarmBadge key="alarm" count={3} />, <ChatbotPill key="chat" />]}
      />

      <ScreenBody>
        <FarmListPanel />

        <div className="flex min-w-0 flex-1 flex-col gap-3.5 overflow-hidden px-5 py-4.5">
          <BannerAlarm />

          <div className="grid grid-cols-6 gap-2.5">
            {HOME_KPIS.map((kpi) => (
              <StatTile key={kpi.label} {...kpi} />
            ))}
          </div>

          <div className="grid min-h-0 flex-1 grid-cols-[1fr_340px] gap-3.5">
            <RackDiagram />
            <div className="flex min-h-0 flex-col gap-3">
              <SelectedCellCard />
              <ScheduleCard />
            </div>
          </div>

          <div className="grid h-[196px] flex-none grid-cols-[1fr_1fr_300px] gap-3.5">
            <TempHumidityCard />
            <EcPhCard />
            <ShortcutsCard />
          </div>
        </div>
      </ScreenBody>
    </Screen>
  );
}

function ChatbotPill() {
  return <span className="rounded-md border border-white/25 px-3 py-1.5">AI 챗봇</span>;
}

/* ── 좌측 농장 목록 ───────────────────────────────────────────────────── */

function FarmListPanel() {
  return (
    <div className="flex w-[246px] flex-none flex-col border-r border-dp-line bg-dp-surface">
      <div className="px-4.5 pt-4 pb-2.5 font-mono text-[11px] leading-none font-semibold tracking-[0.06em] text-dp-muted">
        내 농장 {FARMS.length}
      </div>
      <div className="flex flex-col gap-1.5 px-3">
        {FARMS.map((farm) => {
          const active = farm.name === ACTIVE_FARM;
          return (
            <div
              key={farm.name}
              aria-current={active ? "true" : undefined}
              className={
                active
                  ? "rounded-lg border-[1.5px] border-dp-green bg-dp-green-tint px-3.5 py-3"
                  : "rounded-lg border border-dp-line px-3.5 py-3"
              }
            >
              <div className="flex items-center justify-between">
                <span className="text-[13px] leading-none font-semibold text-dp-ink">{farm.name}</span>
                <StatusDot tone={farm.status} />
              </div>
              <div className="mt-1.5 text-[11.5px] leading-[1.4] text-dp-body">{farm.spec}</div>
              <div className="mt-2 flex gap-2.5 font-mono text-[11px] leading-none font-semibold text-dp-body">
                <span>{farm.temp}</span>
                <span>{farm.humidity}</span>
                <span>{farm.co2}</span>
              </div>
            </div>
          );
        })}
      </div>
      <div className="flex-1" />
      <div className="border-t border-dp-line px-4.5 py-3.5 text-[11.5px] leading-[1.6] text-dp-muted">
        오늘 수확 예정 <b className="text-dp-ink">3랙</b>
        <br />
        미확인 알람 <b className="text-dp-red-ink">3건</b>
      </div>
    </div>
  );
}

/* ── 상단 배너 알람 ───────────────────────────────────────────────────── */

function BannerAlarm() {
  return (
    <div className="flex items-center gap-3 rounded-lg border border-dp-red-line border-l-[3px] border-l-dp-red bg-dp-surface px-3.5 py-2.5">
      <span className="text-[13px] leading-none font-semibold text-dp-red-deep">{HOME_BANNER.title}</span>
      <span className="text-[12px] leading-none text-dp-red-sub">{HOME_BANNER.detail}</span>
      <div className="flex-1" />
      <span className="rounded-md border border-dp-line-strong px-3 py-1.5 text-[12px] leading-none font-medium text-dp-body">
        이력
      </span>
      <Link
        href="/design-preview/alarms"
        className="rounded-md bg-dp-red px-3 py-1.5 text-[12px] leading-none font-semibold text-white"
      >
        조치
      </Link>
    </div>
  );
}

/* ── 랙 배치도 ────────────────────────────────────────────────────────── */

function RackDiagram() {
  return (
    <Card className="flex min-h-0 flex-col px-4.5 py-4">
      <div className="mb-3.5 flex items-center gap-2.5">
        <span className="text-[14px] leading-none font-semibold text-dp-ink">재배동 A · 랙 배치도</span>
        <span className="text-[11.5px] leading-none text-dp-muted">셀 = 랙 1개 층. 색은 목표 대비 편차.</span>
        <div className="flex-1" />
        <RackLegend items={RACK_LEGEND} />
      </div>

      <div className="flex min-h-0 flex-1 items-stretch gap-3.5">
        <div className="flex flex-col justify-around py-0.5 font-mono text-[10.5px] leading-none font-semibold text-dp-muted">
          {RACK_ROWS.map((row) => (
            <span key={row}>{row}</span>
          ))}
        </div>
        <RackGrid cells={RACK_CELLS} columns={RACK_COLUMNS} selected={RACK_SELECTED} />
      </div>

      <div className="mt-2 flex gap-1.5 pl-[38px] font-mono text-[10.5px] leading-none font-semibold text-dp-muted">
        {RACK_COLUMNS.map((col) => (
          <span key={col} className="flex-1 text-center">
            {col}
          </span>
        ))}
      </div>
    </Card>
  );
}

/* ── 선택 셀 상세 ─────────────────────────────────────────────────────── */

function SelectedCellCard() {
  return (
    <Card className="px-4 py-3.5">
      <div className="mb-3 flex items-baseline justify-between">
        <CardTitle>{SELECTED_CELL_DETAIL.title}</CardTitle>
        <span className="font-mono text-[10.5px] leading-none font-semibold text-dp-red-ink">
          {SELECTED_CELL_DETAIL.badge}
        </span>
      </div>
      <div className="grid grid-cols-2 gap-2.5">
        {SELECTED_CELL_DETAIL.metrics.map((m) => (
          <div key={m.label} className="rounded-[7px] bg-dp-inset px-3 py-2.5">
            <div className="text-[11px] leading-none font-medium text-dp-muted">{m.label}</div>
            <div className={`mt-1.5 text-[17px] leading-none font-bold ${m.alert ? "text-dp-red-ink" : "text-dp-ink"}`}>
              {m.value}
            </div>
          </div>
        ))}
      </div>
      <div className="mt-3 flex gap-2">
        <Link
          href="/design-preview/control"
          className="flex-1 rounded-[7px] bg-dp-green py-2.5 text-center text-[12.5px] leading-none font-semibold text-dp-on-green"
        >
          제어 화면
        </Link>
        <Link
          href="/design-preview/data"
          className="flex-1 rounded-[7px] border border-dp-line-strong py-2.5 text-center text-[12.5px] leading-none font-semibold text-dp-body"
        >
          그래프
        </Link>
      </div>
    </Card>
  );
}

/* ── 오늘의 제어 · 스케줄 ─────────────────────────────────────────────── */

function ScheduleCard() {
  return (
    <Card className="flex min-h-0 flex-1 flex-col overflow-hidden px-4 py-3">
      <div className="mb-1 flex items-baseline justify-between">
        <CardTitle>오늘의 제어 · 스케줄</CardTitle>
        <span className="text-[11.5px] leading-none text-dp-muted">08.23</span>
      </div>
      <div className="flex flex-col">
        {SCHEDULE.map((row, i) => (
          <div
            key={row.time}
            className={`flex items-center gap-2.5 py-1.5 ${i < SCHEDULE.length - 1 ? "border-b border-dp-line" : ""}`}
          >
            <span className="w-[38px] font-mono text-[11px] leading-none font-semibold text-dp-muted">{row.time}</span>
            <span
              className={`flex-1 text-[12.5px] leading-none font-medium ${
                row.tone === "muted" ? "text-dp-body" : "text-dp-ink"
              }`}
            >
              {row.label}
            </span>
            <span
              className={`text-[11px] leading-none font-semibold ${
                row.tone === "ok" ? "text-dp-green" : row.tone === "alert" ? "text-dp-red-ink" : "text-dp-muted"
              }`}
            >
              {row.status}
            </span>
          </div>
        ))}
      </div>
      <div className="flex-1" />
      <div className="mt-2.5 flex justify-between border-t border-dp-line pt-2.5 text-[11.5px] leading-none text-dp-muted">
        <span>자동 운전 중</span>
        <span className="font-semibold text-dp-green">스케줄 편집</span>
      </div>
    </Card>
  );
}

/* ── 하단 그래프 3종 ──────────────────────────────────────────────────── */

function TempHumidityCard() {
  return (
    <Card className="flex flex-col px-4.5 py-4">
      <div className="mb-2.5 flex items-baseline gap-2.5">
        <CardTitle>온도 · 습도 24시간</CardTitle>
        <span className="text-[11px] leading-none text-dp-muted">1분 간격</span>
        <div className="flex-1" />
        <LegendKey tone="green" label="온도" />
        <LegendKey tone="blue" label="습도" />
      </div>
      <LineChart
        className="flex-1 bg-[linear-gradient(to_top,var(--dp-line)_1px,transparent_1px)] bg-[length:100%_25%]"
        viewBox="0 0 400 100"
        lines={[
          { points: TEMP_SERIES, tone: "green" },
          { points: HUMIDITY_SERIES, tone: "blue" },
        ]}
      />
      <AxisLabels labels={["00", "06", "12", "18", "24"]} />
    </Card>
  );
}

function EcPhCard() {
  return (
    <Card className="flex flex-col px-4.5 py-4">
      <div className="mb-2.5 flex items-baseline gap-2.5">
        <CardTitle>EC · pH 추이</CardTitle>
        <div className="flex-1" />
        <span className="text-[11px] leading-none text-dp-muted">목표대 표시</span>
      </div>
      <LineChart
        className="flex-1"
        viewBox="0 0 400 100"
        lines={[
          { points: EC_SERIES, tone: "red" },
          { points: EC_TARGET_SERIES, tone: "muted", width: 1.5, dashed: true },
        ]}
      >
        {/* 목표대 밴드 */}
        <div className="absolute top-[38%] right-0 left-0 h-[26%] border-t border-b border-dashed border-dp-green-line bg-dp-green-tint-2" />
      </LineChart>
      <AxisLabels labels={["08:00", "11:00", "14:00", "지금"]} />
    </Card>
  );
}

function ShortcutsCard() {
  return (
    <Card className="flex flex-col px-4 py-4">
      <div className="mb-3">
        <CardTitle>바로가기</CardTitle>
      </div>
      <div className="grid grid-cols-2 gap-2">
        {HOME_SHORTCUTS.map((label, i) => (
          <span
            key={label}
            className={`rounded-[7px] py-2.5 text-center text-[12px] leading-none font-semibold ${
              i === 0
                ? "border border-dp-green-line bg-dp-green-tint text-dp-green-ink"
                : "border border-dp-line bg-dp-inset text-dp-body"
            }`}
          >
            {label}
          </span>
        ))}
      </div>
      <div className="flex-1" />
      <div className="mt-2.5 border-t border-dp-line pt-2.5 text-[11.5px] leading-[1.5] text-dp-muted">
        {HOME_WEATHER_NOTE[0]}
        <br />
        {HOME_WEATHER_NOTE[1]}
      </div>
    </Card>
  );
}
