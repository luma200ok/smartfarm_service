"use client";

import { useState } from "react";
import { DETAIL_COLS, PageTitle, Screen, ScreenBody, TopBar } from "@/components/design-preview/chrome";
import {
  CONTROL_APPLY_HISTORY,
  CONTROL_LAST_CHANGE,
  CONTROL_ZONES,
  DEVICE_TOGGLES,
  PENDING_CHANGES,
  SETPOINTS,
  UNREAD_ALARMS,
} from "@/components/design-preview/mock";
import {
  Card,
  CardTitle,
  Chip,
  Gauge,
  GhostButton,
  PrimaryButton,
  TimelineRow,
  Toggle,
} from "@/components/design-preview/ui";

// 화면 02 — 복합환경제어. 핸드오프 `Screens › 02` + `Interactions › 제어`.
// −/+ 는 서버로 바로 보내지 않고 **적용 대기 큐**에 쌓이며, 큐에 항목이 있을 때만
// `변경 적용` 버튼이 활성이 되고 개수를 표시한다. 되돌리기는 큐 전체를 버린다.
// 자동 운전 OFF면 목표값 카드가 비활성(opacity .5)되고 장비 수동 조작만 유효하다.
export default function DesignPreviewControlPage() {
  const [zone, setZone] = useState(CONTROL_ZONES[0]);
  const [auto, setAuto] = useState(true);
  const [values, setValues] = useState<Record<string, number>>(() =>
    Object.fromEntries(SETPOINTS.map((s) => [s.key, s.value])),
  );
  const [devices, setDevices] = useState<Record<string, boolean>>(() =>
    Object.fromEntries(DEVICE_TOGGLES.map((d) => [d.key, d.on])),
  );
  const [queue, setQueue] = useState(PENDING_CHANGES);
  const [offlineNotice, setOfflineNotice] = useState(false);

  function nudge(key: string, step: number) {
    setValues((prev) => ({ ...prev, [key]: Number((prev[key] + step).toFixed(2)) }));
  }

  return (
    <Screen>
      <TopBar status={[auto ? "자동 운전 중" : "수동 운전", `알람 ${UNREAD_ALARMS}`]} />

      <ScreenBody
        section="control"
        asideBottom
        aside={
          <div className="rounded-[7px] bg-dp-inset px-3 py-[11px] text-[11.5px] leading-[1.5] text-dp-sub">
            최근 변경
            <br />
            <b className="text-dp-ink">{CONTROL_LAST_CHANGE.time}</b> {CONTROL_LAST_CHANGE.what}
          </div>
        }
      >
        <PageTitle title="복합환경제어">
          <div className="flex gap-1.5">
            {CONTROL_ZONES.map((z) => (
              <Chip key={z} as="button" active={z === zone} onClick={() => setZone(z)}>
                {z}
              </Chip>
            ))}
          </div>
          <div className="hidden flex-1 min-[768px]:block" />
          <div className="flex items-center gap-2.5 rounded-[20px] border border-dp-line-2 bg-dp-surface px-3 py-1.5 text-[12px] leading-none font-medium text-dp-ink">
            <Toggle on={auto} size="sm" label="자동 운전" onChange={() => setAuto((v) => !v)} />
            자동 운전
          </div>
          {queue.length > 0 ? (
            <PrimaryButton>변경 적용</PrimaryButton>
          ) : (
            <span className="rounded-[7px] bg-dp-off px-3.5 py-2 text-[12.5px] leading-none font-semibold text-dp-faint">
              변경 적용
            </span>
          )}
        </PageTitle>

        {/* 목표값 카드 4열 — 자동 운전 OFF면 비활성 */}
        <div
          className={`grid grid-cols-1 gap-3 min-[640px]:grid-cols-2 min-[1440px]:grid-cols-4 ${auto ? "" : "pointer-events-none opacity-50"}`}
          aria-disabled={!auto}
        >
          {SETPOINTS.map((sp) => (
            <Card key={sp.key} className="px-4 py-[15px]">
              <div className="flex items-baseline justify-between">
                <span className="text-[12.5px] leading-none font-semibold text-dp-ink">{sp.label}</span>
                <span
                  className={`text-[11px] leading-none font-medium ${
                    sp.statusTone === "ok" ? "text-dp-green" : "text-dp-muted"
                  }`}
                >
                  {sp.status}
                </span>
              </div>
              <div className="mt-3 mb-1 flex items-baseline gap-2">
                <span className="text-[26px] leading-none font-bold text-dp-ink">{sp.format(values[sp.key])}</span>
                <span className="text-[12px] leading-none font-medium text-dp-muted">{sp.target}</span>
              </div>
              <div className="mt-3">
                <Gauge fill={sp.fill} marker={sp.marker} />
              </div>
              <div className="mt-2.5 flex gap-1.5">
                <StepButton label={`${sp.label} 낮추기`} onClick={() => nudge(sp.key, -sp.step)}>
                  −
                </StepButton>
                <StepButton label={`${sp.label} 높이기`} onClick={() => nudge(sp.key, sp.step)}>
                  +
                </StepButton>
              </div>
            </Card>
          ))}
        </div>

        <div className={DETAIL_COLS[430]}>
          {/* 장비 수동 조작 */}
          <Card className="flex flex-col px-[18px] py-4">
            <div className="mb-3 flex flex-wrap items-baseline gap-2.5">
              <CardTitle size="lg">장비 수동 조작</CardTitle>
              <span className="text-[11.5px] leading-none text-dp-muted">
                {auto ? "자동 운전 중에는 임시 조작만 반영됩니다" : "수동 운전 — 조작이 다음 주기까지 유지됩니다"}
              </span>
            </div>

            <div className="grid grid-cols-1 gap-2.5 min-[768px]:grid-cols-2">
              {DEVICE_TOGGLES.map((device) => {
                const on = devices[device.key];
                return (
                  <div
                    key={device.key}
                    className={`flex items-center gap-3 rounded-lg px-3.5 py-3 ${
                      device.offline ? "border border-dp-red-line bg-dp-red-tint" : "border border-dp-line-2"
                    }`}
                  >
                    <span className="min-w-0 flex-1 truncate text-[13px] leading-none font-semibold text-dp-ink">
                      {device.label}
                    </span>
                    <span
                      className={`flex-none text-[11.5px] leading-none font-medium ${
                        device.offline ? "text-dp-red-ink" : on ? "text-dp-green" : "text-dp-muted"
                      }`}
                    >
                      {device.offline ? device.offLabel : on ? device.onLabel : device.offLabel}
                    </span>
                    <Toggle
                      on={on}
                      blocked={device.offline}
                      label={device.label}
                      onChange={() =>
                        device.offline
                          ? setOfflineNotice(true)
                          : setDevices((prev) => ({ ...prev, [device.key]: !prev[device.key] }))
                      }
                    />
                  </div>
                );
              })}
            </div>

            <div className="flex-1" />
            <div className="mt-3 flex flex-wrap items-center gap-2.5 border-t border-dp-line pt-[11px]">
              <span className="text-[11.5px] leading-none text-dp-muted">
                {offlineNotice
                  ? "통신이 복구되어야 조작할 수 있습니다"
                  : "비상 정지 시 모든 장비가 즉시 정지하고 자동 운전이 해제됩니다"}
              </span>
              <div className="flex-1" />
              <span className="rounded-[7px] border border-dp-red px-4 py-2 text-[12.5px] leading-none font-semibold text-dp-red-ink">
                비상 정지
              </span>
            </div>
          </Card>

          {/* 적용 대기 변경 + 최근 적용 이력 */}
          <Card className="flex flex-col px-4 py-[15px]">
            <div className="mb-3">
              <CardTitle>적용 대기 변경</CardTitle>
            </div>

            {queue.length === 0 ? (
              <p className="rounded-lg bg-dp-inset px-3 py-3 text-[11.5px] leading-[1.6] text-dp-sub">
                대기 중인 변경이 없습니다. 목표값을 조정하면 여기에 쌓입니다.
              </p>
            ) : (
              <div className="flex flex-col gap-2">
                {queue.map((change) => (
                  <div key={change.id} className="flex items-start gap-2 rounded-lg bg-dp-inset px-3 py-[11px]">
                    <span className="min-w-0 flex-1">
                      <span className="block text-[12.5px] leading-[1.4] font-semibold text-dp-ink">
                        {change.title}
                      </span>
                      <span className="mt-1.5 block text-[11.5px] leading-none text-dp-muted">{change.detail}</span>
                    </span>
                    <button
                      type="button"
                      aria-label={`${change.title} 삭제`}
                      onClick={() => setQueue((prev) => prev.filter((c) => c.id !== change.id))}
                      className="flex-none text-[13px] leading-none text-dp-muted hover:text-dp-ink"
                    >
                      ×
                    </button>
                  </div>
                ))}
              </div>
            )}

            <div className="mt-3.5 border-t border-dp-line pt-3">
              <div className="mb-2.5 text-[12.5px] leading-none font-semibold text-dp-ink">최근 적용 이력</div>
              {CONTROL_APPLY_HISTORY.map((row, i) => (
                <TimelineRow
                  key={row.time + row.text}
                  time={row.time}
                  text={row.text}
                  dim={row.dim}
                  last={i === CONTROL_APPLY_HISTORY.length - 1}
                />
              ))}
            </div>

            <div className="flex-1" />
            <div className="mt-3 flex gap-2">
              <button type="button" onClick={() => setQueue([])} className="flex-1">
                <GhostButton className="block text-center">되돌리기</GhostButton>
              </button>
              <PrimaryButton className="flex-1 text-center">{queue.length}건 적용</PrimaryButton>
            </div>
          </Card>
        </div>
      </ScreenBody>
    </Screen>
  );
}

function StepButton({ children, label, onClick }: { children: string; label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className="flex-1 rounded-md border border-dp-line-strong py-[7px] text-center text-[13px] leading-none font-semibold text-dp-body transition-colors hover:bg-dp-inset"
    >
      {children}
    </button>
  );
}
