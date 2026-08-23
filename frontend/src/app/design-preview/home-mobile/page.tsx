import {
  ACTIVE_FARM,
  HOME_BANNER,
  RACK_CELLS,
  RACK_COLUMNS,
  RACK_SELECTED,
} from "@/components/design-preview/mock";
import { Card, RackGrid } from "@/components/design-preview/ui";

// 시안 1c — 홈 · 모바일 390. 농장 전환 + 도면 축약 + 하단 4탭.
// 데스크톱 브라우저에서 보게 되므로 390×844 프레임을 가운데 띄운다.
// 하단 탭은 IA 원칙대로 4개(홈·제어·알람·더보기)만 — 데이터/관리는 더보기 안으로.

const MOBILE_KPIS = [
  { label: "온도", value: "23.8°", alert: false },
  { label: "습도", value: "68%", alert: false },
  { label: "CO₂", value: "1,020", alert: false },
  { label: "PPFD", value: "218", alert: false },
  { label: "EC", value: "2.6", alert: true },
  { label: "pH", value: "5.9", alert: false },
];

const MOBILE_SCHEDULE = [
  { time: "14:00", label: "양액 2회차", status: "EC 이상", alert: true },
  { time: "16:00", label: "CO₂ 시비 종료", status: "대기", alert: false },
];

const TABS = ["홈", "제어", "알람", "더보기"];

export default function DesignPreviewHomeMobilePage() {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-dp-canvas px-4 py-10">
      <div className="w-[390px] overflow-hidden rounded-[10px] border border-dp-line bg-dp-surface shadow-sm">
        <div className="flex h-[844px] flex-col bg-dp-canvas font-dp-sans">
          <MobileHeader />

          <div className="flex flex-1 flex-col gap-3 overflow-hidden px-4 py-3.5">
            <MobileBanner />
            <MobileKpiGrid />
            <MobileRackCard />
            <MobileScheduleCard />
          </div>

          <MobileTabBar />
        </div>
      </div>
    </div>
  );
}

function MobileHeader() {
  return (
    <div className="flex-none bg-dp-bar px-4.5 py-3.5 text-white">
      <div className="flex items-center gap-2.5">
        <div className="text-[14px] leading-none font-bold">
          스마트팜 <span className="text-[#5fd08c]">DFX</span>
        </div>
        <div className="flex-1" />
        <div className="flex items-center gap-1.5 text-[11.5px] leading-none text-white/70">
          알람
          <span className="rounded-[9px] bg-dp-red px-1.5 py-0.5 font-mono text-[10px] leading-[1.4] font-semibold text-white">
            3
          </span>
        </div>
        <div className="flex h-6 w-6 items-center justify-center rounded-full bg-dp-green text-[10.5px] leading-none font-semibold text-dp-on-green">
          김
        </div>
      </div>
      <div className="mt-3 flex items-center justify-between rounded-[9px] bg-white/10 px-3.5 py-3">
        <div>
          <div className="text-[13.5px] leading-none font-semibold">{ACTIVE_FARM}</div>
          <div className="mt-1.5 text-[11px] leading-none text-white/55">12랙 · 로메인 · 통신 정상</div>
        </div>
        <span className="text-[13px] opacity-55">전환 ▾</span>
      </div>
    </div>
  );
}

function MobileBanner() {
  return (
    <div className="rounded-[9px] border border-dp-red-line border-l-[3px] border-l-dp-red bg-dp-surface px-3.5 py-3">
      <div className="text-[13px] leading-[1.4] font-semibold text-dp-red-deep">{HOME_BANNER.mobileTitle}</div>
      <div className="mt-1.5 text-[11.5px] leading-[1.4] text-dp-red-sub">{HOME_BANNER.mobileDetail}</div>
      <div className="mt-2.5 flex gap-2">
        <span className="flex-1 rounded-[7px] bg-dp-red py-2.5 text-center text-[12.5px] leading-none font-semibold text-white">
          조치하기
        </span>
        <span className="flex-1 rounded-[7px] border border-dp-line-strong py-2.5 text-center text-[12.5px] leading-none font-semibold text-dp-body">
          나중에
        </span>
      </div>
    </div>
  );
}

function MobileKpiGrid() {
  return (
    <div className="grid grid-cols-3 gap-2">
      {MOBILE_KPIS.map((kpi) => (
        <Card key={kpi.label} className="px-3 py-2.5">
          <div className="text-[11px] leading-none font-medium text-dp-muted">{kpi.label}</div>
          <div className={`mt-1.5 text-[20px] leading-none font-bold ${kpi.alert ? "text-dp-red-ink" : "text-dp-ink"}`}>
            {kpi.value}
          </div>
        </Card>
      ))}
    </div>
  );
}

function MobileRackCard() {
  return (
    <Card className="px-3.5 py-3">
      <div className="mb-2.5 flex items-baseline gap-2">
        <span className="text-[13px] leading-none font-semibold text-dp-ink">랙 배치도</span>
        <span className="text-[11px] leading-none text-dp-muted">A동 · 12랙 5층</span>
        <div className="flex-1" />
        <span className="text-[11px] leading-none font-semibold text-dp-green">확대</span>
      </div>
      <RackGrid
        cells={RACK_CELLS}
        columns={RACK_COLUMNS}
        selected={RACK_SELECTED}
        cellClass="rounded-[3px]"
        gapClass="gap-[3px]"
        rowsClass="[grid-template-rows:repeat(5,16px)]"
      />
    </Card>
  );
}

function MobileScheduleCard() {
  return (
    <Card className="px-3.5 py-3">
      <div className="mb-2 flex items-baseline">
        <span className="text-[13px] leading-none font-semibold text-dp-ink">오늘의 제어</span>
        <div className="flex-1" />
        <span className="text-[11px] leading-none text-dp-muted">자동 운전 중</span>
      </div>
      {MOBILE_SCHEDULE.map((row, i) => (
        <div
          key={row.time}
          className={`flex items-center gap-2.5 py-2 ${i < MOBILE_SCHEDULE.length - 1 ? "border-b border-dp-line" : ""}`}
        >
          <span className="w-9 font-mono text-[11px] leading-none font-semibold text-dp-muted">{row.time}</span>
          <span className={`flex-1 text-[12.5px] leading-none font-medium ${row.alert ? "text-dp-ink" : "text-dp-body"}`}>
            {row.label}
          </span>
          <span
            className={`text-[11px] leading-none font-semibold ${row.alert ? "text-dp-red-ink" : "text-dp-muted"}`}
          >
            {row.status}
          </span>
        </div>
      ))}
    </Card>
  );
}

function MobileTabBar() {
  return (
    <div className="grid flex-none grid-cols-4 gap-0.5 border-t border-dp-line bg-dp-surface px-2 pt-2.5 pb-6.5">
      {TABS.map((tab, i) => (
        <div key={tab} className="flex flex-col items-center gap-1.5 py-2">
          <span className={`h-5 w-5 rounded-[5px] ${i === 0 ? "bg-dp-green" : "bg-dp-track"}`} />
          <span
            className={`text-[11px] leading-none ${
              i === 0 ? "font-semibold text-dp-green" : "font-medium text-dp-muted"
            }`}
          >
            {tab}
          </span>
        </div>
      ))}
    </div>
  );
}
