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
  CONTROL_LAST_CHANGE,
  DEVICE_TOGGLES,
  PENDING_CHANGES,
  SETPOINTS,
} from "@/components/design-preview/mock";
import {
  Card,
  CardTitle,
  Chip,
  Gauge,
  GhostButton,
  PrimaryButton,
  Toggle,
} from "@/components/design-preview/ui";

// 시안 2a — 제어 · 복합환경제어.
// 목업이지만 "조작하는 화면"이라는 점이 요지라, 목표값 ±·장비 토글·자동 운전 스위치는
// 로컬 상태로 실제 반응하게 만들었다. 서버로 나가는 요청은 없다.

const ZONES = ["A동", "B동", "전체"];

export default function DesignPreviewControlPage() {
  const [zone, setZone] = useState(ZONES[0]);
  const [auto, setAuto] = useState(true);
  const [values, setValues] = useState<Record<string, number>>(() =>
    Object.fromEntries(SETPOINTS.map((s) => [s.key, s.value])),
  );
  const [devices, setDevices] = useState<Record<string, boolean>>(() =>
    Object.fromEntries(DEVICE_TOGGLES.map((d) => [d.key, d.on])),
  );

  function nudge(key: string, step: number) {
    setValues((prev) => ({ ...prev, [key]: Number((prev[key] + step).toFixed(2)) }));
  }

  return (
    <Screen>
      <TopBar status={[auto ? "자동 운전 중" : "수동 운전", "알람 3"]} compact />

      <ScreenBody>
        <SubNav section="제어">
          <div className="flex-1" />
          <div className="rounded-[7px] bg-dp-inset px-3 py-2.5 text-[11.5px] leading-[1.5] text-dp-body">
            최근 변경
            <br />
            <b className="text-dp-ink">{CONTROL_LAST_CHANGE.time}</b> {CONTROL_LAST_CHANGE.what}
          </div>
        </SubNav>

        <ScreenMain>
          <PageTitle title="복합환경제어">
            <div className="ml-1.5 flex gap-1.5">
              {ZONES.map((z) => (
                <Chip key={z} as="button" active={z === zone} onClick={() => setZone(z)}>
                  {z}
                </Chip>
              ))}
            </div>
            <div className="flex-1" />
            <div className="flex items-center gap-2.5 rounded-[20px] border border-dp-line bg-dp-surface px-3 py-1.5 text-[12px] leading-none font-medium text-dp-ink">
              <Toggle on={auto} size="sm" label="자동 운전" onChange={() => setAuto((v) => !v)} />
              자동 운전
            </div>
            <PrimaryButton>변경 적용</PrimaryButton>
          </PageTitle>

          <div className="grid grid-cols-4 gap-3">
            {SETPOINTS.map((sp) => (
              <Card key={sp.key} className="px-4 py-3.5">
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
                  <span className="text-[26px] leading-none font-bold text-dp-ink">
                    {sp.format(values[sp.key])}
                  </span>
                  <span className="text-[12px] leading-none font-medium text-dp-muted">{sp.target}</span>
                </div>
                <Gauge fill={sp.fill} marker={sp.marker} />
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

          <div className="grid min-h-0 flex-1 grid-cols-[1fr_330px] gap-3.5">
            <Card className="flex flex-col overflow-hidden px-4.5 py-4">
              <div className="mb-3 flex items-baseline gap-2.5">
                <span className="text-[14px] leading-none font-semibold text-dp-ink">장비 수동 조작</span>
                <span className="text-[11.5px] leading-none text-dp-muted">
                  {auto ? "자동 운전 중에는 임시 조작만 반영됩니다" : "수동 운전 — 조작이 그대로 유지됩니다"}
                </span>
              </div>

              <div className="grid grid-cols-2 gap-2.5">
                {DEVICE_TOGGLES.map((device) => {
                  const on = devices[device.key];
                  return (
                    <div
                      key={device.key}
                      className={`flex items-center gap-3 rounded-lg border px-3.5 py-3 ${
                        device.offline ? "border-dp-red-line bg-dp-red-tint" : "border-dp-line"
                      }`}
                    >
                      <span className="flex-1 text-[13px] leading-none font-semibold text-dp-ink">{device.label}</span>
                      <span
                        className={`text-[11.5px] leading-none font-medium ${
                          device.offline ? "text-dp-red-ink" : on ? "text-dp-green" : "text-dp-muted"
                        }`}
                      >
                        {device.offline ? device.offLabel : on ? device.onLabel : device.offLabel}
                      </span>
                      <Toggle
                        on={on}
                        disabled={device.offline}
                        label={device.label}
                        onChange={() => setDevices((prev) => ({ ...prev, [device.key]: !prev[device.key] }))}
                      />
                    </div>
                  );
                })}
              </div>

              <div className="flex-1" />
              <div className="mt-3 flex items-center gap-2.5 border-t border-dp-line pt-3">
                <span className="text-[11.5px] leading-none text-dp-muted">
                  비상 정지 시 모든 장비가 즉시 정지하고 자동 운전이 해제됩니다
                </span>
                <div className="flex-1" />
                <span className="rounded-[7px] border border-dp-red px-4 py-2 text-[12.5px] leading-none font-semibold text-dp-red-ink">
                  비상 정지
                </span>
              </div>
            </Card>

            <Card className="flex flex-col overflow-hidden px-4 py-3.5">
              <div className="mb-3">
                <CardTitle>적용 대기 변경</CardTitle>
              </div>
              <div className="flex flex-col gap-2">
                {PENDING_CHANGES.map((change) => (
                  <div key={change.title} className="rounded-lg bg-dp-inset px-3 py-3">
                    <div className="text-[12.5px] leading-[1.4] font-semibold text-dp-ink">{change.title}</div>
                    <div className="mt-1.5 text-[11.5px] leading-none text-dp-muted">{change.detail}</div>
                  </div>
                ))}
              </div>
              <div className="flex-1" />
              <div className="mt-3 flex gap-2">
                <GhostButton className="flex-1 text-center">되돌리기</GhostButton>
                <PrimaryButton className="flex-1 text-center">{PENDING_CHANGES.length}건 적용</PrimaryButton>
              </div>
            </Card>
          </div>
        </ScreenMain>
      </ScreenBody>
    </Screen>
  );
}

function StepButton({
  children,
  label,
  onClick,
}: {
  children: string;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className="flex-1 rounded-md border border-dp-line-strong py-2 text-center text-[13px] leading-none font-semibold text-dp-body transition-colors hover:bg-dp-inset"
    >
      {children}
    </button>
  );
}
