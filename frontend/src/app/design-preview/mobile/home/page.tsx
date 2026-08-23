import { MobileFrame } from "@/components/design-preview/MobileFrame";
import {
  ACTIVE_FARM,
  M_HOME_BANNER,
  M_HOME_METRICS,
  M_HOME_SCHEDULE,
  RACK_CELLS,
  RACK_COLUMNS,
  RACK_SELECTED,
  UNREAD_ALARMS,
} from "@/components/design-preview/mock";
import { Card, RackGrid } from "@/components/design-preview/ui";

// 화면 M1 — 모바일 홈. 핸드오프 `Mobile`.
// 상단 농장 전환 바 + 미확인 경보 카드 + 지표 3열×2행 + 랙 도면 축약 + 오늘의 제어.
// 주요 버튼 높이 48px, 터치 타깃 최소 44px.
export default function MobileHomePage() {
  return (
    <MobileFrame
      activeTab="home"
      header={
        <div className="px-4.5 py-3.5">
          <div className="flex items-center gap-2.5">
            <div className="text-[14px] leading-none font-bold">
              스마트팜 <span className="text-[#5fd08c]">DFX</span>
            </div>
            <div className="flex-1" />
            <div className="flex items-center gap-1.5 text-[11.5px] leading-none text-white/70">
              알람
              <span className="rounded-[9px] bg-dp-red px-1.5 py-0.5 font-mono text-[10px] leading-[1.4] font-semibold text-white">
                {UNREAD_ALARMS}
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
      }
    >
      {/* 미확인 경보 — 액션 2개 노출 */}
      <div className="rounded-[9px] border border-dp-red-line border-l-[3px] border-l-dp-red bg-dp-surface px-3.5 py-3">
        <div className="text-[13px] leading-[1.4] font-semibold text-dp-red-deep">{M_HOME_BANNER.title}</div>
        <div className="mt-1.5 text-[11.5px] leading-[1.4] text-dp-red-sub">{M_HOME_BANNER.detail}</div>
        <div className="mt-2.5 flex gap-2">
          <span className="flex-1 rounded-[7px] bg-dp-red py-3 text-center text-[12.5px] leading-none font-semibold text-white">
            조치하기
          </span>
          <span className="flex-1 rounded-[7px] border border-dp-line-strong py-3 text-center text-[12.5px] leading-none font-semibold text-dp-body">
            나중에
          </span>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-2">
        {M_HOME_METRICS.map((kpi) => (
          <Card key={kpi.label} className="rounded-[9px] px-3 py-2.5">
            <div className="text-[11px] leading-none font-medium text-dp-muted">{kpi.label}</div>
            <div
              className={`mt-1.5 text-[20px] leading-none font-bold ${kpi.alert ? "text-dp-red-ink" : "text-dp-ink"}`}
            >
              {kpi.value}
            </div>
          </Card>
        ))}
      </div>

      <Card className="px-3.5 py-3">
        <div className="mb-2.5 flex items-baseline gap-2">
          <span className="text-[13px] leading-none font-semibold text-dp-ink">랙 배치도</span>
          <span className="text-[11px] leading-none text-dp-muted">A동 · 12랙 5층</span>
          <div className="flex-1" />
          <span className="text-[11px] leading-none font-semibold text-dp-green">확대</span>
        </div>
        <div className="overflow-x-auto">
          <RackGrid
            cells={RACK_CELLS}
            columns={RACK_COLUMNS}
            selected={RACK_SELECTED}
            minCell={14}
            rowHeight={16}
            cellClass="rounded-[3px]"
            gapClass="gap-[3px]"
          />
        </div>
      </Card>

      <Card className="px-3.5 py-3">
        <div className="mb-2 flex items-baseline">
          <span className="text-[13px] leading-none font-semibold text-dp-ink">오늘의 제어</span>
          <div className="flex-1" />
          <span className="text-[11px] leading-none text-dp-muted">자동 운전 중</span>
        </div>
        {M_HOME_SCHEDULE.map((row, i) => (
          <div
            key={row.time}
            className={`flex items-center gap-2.5 py-2 ${i < M_HOME_SCHEDULE.length - 1 ? "border-b border-dp-line-row" : ""}`}
          >
            <span className="w-9 font-mono text-[11px] leading-none font-semibold text-dp-muted">{row.time}</span>
            <span
              className={`flex-1 text-[12.5px] leading-none font-medium ${row.alert ? "text-dp-ink" : "text-dp-body"}`}
            >
              {row.label}
            </span>
            <span className={`text-[11px] leading-none font-semibold ${row.alert ? "text-dp-red-ink" : "text-dp-muted"}`}>
              {row.status}
            </span>
          </div>
        ))}
      </Card>
    </MobileFrame>
  );
}
