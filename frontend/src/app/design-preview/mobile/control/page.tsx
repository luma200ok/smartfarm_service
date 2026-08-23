"use client";

import { useState } from "react";
import { MobileApplyBar, MobileFrame } from "@/components/design-preview/MobileFrame";
import {
  CONTROL_ZONES,
  formatSetpoint,
  M_CONTROL_DEVICES,
  M_CONTROL_SECONDARY,
  SETPOINTS,
} from "@/components/design-preview/mock";
import { Card, Gauge, Toggle } from "@/components/design-preview/ui";

// 화면 M2 — 모바일 제어. 핸드오프 `Mobile`.
// 온도만 큰 카드(값 30px, 진행 바 6px, −/+ 전폭)로 강조하고 나머지는 2열 요약 카드.
// 적용 버튼은 하단 고정 바에 두고 되돌리기 : n건 적용 = 1 : 1.4.
// 배열 순서가 아니라 key로 찾는다 — SETPOINTS 순서가 바뀌어도 온도 카드가 조용히 틀어지지 않게.
const TEMP = SETPOINTS.find((s) => s.key === "temp") ?? SETPOINTS[0];

export default function MobileControlPage() {
  const [zone, setZone] = useState(CONTROL_ZONES[0]);
  const [auto, setAuto] = useState(true);
  const [temp, setTemp] = useState(TEMP.value);
  const [devices, setDevices] = useState<Record<string, boolean>>(() =>
    Object.fromEntries(M_CONTROL_DEVICES.map((d) => [d.key, d.on])),
  );

  const pending = temp === TEMP.value ? 0 : 1;

  return (
    <MobileFrame
      activeTab="control"
      bottomAction={<MobileApplyBar count={pending} />}
      header={
        <div className="px-4.5 py-3.5">
          <div className="flex items-center gap-2.5">
            <span className="text-[13px] leading-none font-medium text-white/60">&lt;</span>
            <div className="text-[14.5px] leading-none font-semibold">복합환경제어</div>
            <div className="flex-1" />
            <div className="flex items-center gap-[7px] text-[11.5px] leading-none font-medium text-white/75">
              <Toggle on={auto} size="sm" label="자동 운전" onChange={() => setAuto((v) => !v)} />
              자동
            </div>
          </div>
          <div className="mt-3 flex gap-1.5">
            {CONTROL_ZONES.map((z) => (
              <button
                key={z}
                type="button"
                aria-pressed={z === zone}
                onClick={() => setZone(z)}
                className={`rounded-[7px] px-3.5 py-2.5 text-[12.5px] leading-none ${
                  z === zone ? "bg-white/[0.16] font-semibold text-white" : "font-normal text-white/60"
                }`}
              >
                {z}
              </button>
            ))}
          </div>
        </div>
      }
    >
      {/* 온도 — 강조 카드 */}
      <Card className={`px-4 py-4 ${auto ? "" : "opacity-50"}`}>
        <div className="flex items-baseline">
          <span className="text-[13.5px] leading-none font-semibold text-dp-ink">온도</span>
          <div className="flex-1" />
          <span className="text-[11.5px] leading-none font-medium text-dp-green">정상</span>
        </div>
        <div className="mt-3 mb-1 flex items-baseline gap-2.5">
          <span className="text-[30px] leading-none font-bold text-dp-ink">{formatSetpoint(TEMP.key, temp)}</span>
          <span className="text-[12.5px] leading-none font-medium text-dp-muted">{TEMP.target}</span>
        </div>
        <div className="mt-3.5">
          <Gauge fill={TEMP.fill} marker={TEMP.marker} tall />
        </div>
        <div className="mt-3 flex gap-2">
          <StepButton label="온도 낮추기" onClick={() => setTemp((v) => Number((v - 0.1).toFixed(1)))}>
            −
          </StepButton>
          <StepButton label="온도 높이기" onClick={() => setTemp((v) => Number((v + 0.1).toFixed(1)))}>
            +
          </StepButton>
        </div>
      </Card>

      {/* 나머지 항목 — 2열 요약 */}
      <div className="grid grid-cols-2 gap-2.5">
        {M_CONTROL_SECONDARY.map((item) => (
          <Card key={item.label} className="px-3.5 py-3">
            <div className="flex items-baseline">
              <span className="text-[12.5px] leading-none font-semibold text-dp-ink">{item.label}</span>
              <div className="flex-1" />
              <span
                className={`text-[11px] leading-none font-medium ${
                  item.statusTone === "ok"
                    ? "text-dp-green"
                    : item.statusTone === "alert"
                      ? "text-dp-red-ink"
                      : "text-dp-muted"
                }`}
              >
                {item.status}
              </span>
            </div>
            <div
              className={`mt-2.5 mb-1 text-[21px] leading-none font-bold ${item.alert ? "text-dp-red-ink" : "text-dp-ink"}`}
            >
              {item.value}
            </div>
            <div className="text-[11px] leading-none font-medium text-dp-muted">{item.target}</div>
          </Card>
        ))}
      </div>

      <Card className="px-4 py-3.5">
        <div className="mb-3 flex items-baseline">
          <span className="text-[13.5px] leading-none font-semibold text-dp-ink">장비</span>
          <div className="flex-1" />
          <span className="text-[11.5px] leading-none text-dp-muted">6대 · 이상 1</span>
        </div>
        {M_CONTROL_DEVICES.map((device, i) => (
          <div
            key={device.key}
            className={`flex items-center gap-3 py-[11px] ${i < M_CONTROL_DEVICES.length - 1 ? "border-b border-dp-line-row" : ""}`}
          >
            <span className="flex-1 text-[13px] leading-none font-semibold text-dp-ink">{device.label}</span>
            <span
              className={`text-[11.5px] leading-none font-medium ${
                device.tone === "ok" ? "text-dp-green" : device.tone === "alert" ? "text-dp-red-ink" : "text-dp-muted"
              }`}
            >
              {device.state}
            </span>
            {/* PC 제어 화면은 blocked(클릭 시 안내 문구)를 쓰지만, 모바일에는 안내를 띄울 자리가
                없어 완전 비활성(disabled)으로 둔다 — 상태 텍스트 "통신 없음"이 이유를 대신한다. */}
            <Toggle
              on={devices[device.key]}
              size="touch"
              disabled={device.offline}
              label={device.label}
              onChange={() => setDevices((prev) => ({ ...prev, [device.key]: !prev[device.key] }))}
            />
          </div>
        ))}
      </Card>
    </MobileFrame>
  );
}

function StepButton({ children, label, onClick }: { children: string; label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className="flex-1 rounded-lg border border-dp-line-strong py-3.5 text-center text-[16px] leading-none font-semibold text-dp-body"
    >
      {children}
    </button>
  );
}
