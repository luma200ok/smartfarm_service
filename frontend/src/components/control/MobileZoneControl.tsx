"use client";

import {
  CONTROLLABLE_METRICS,
  DEVICE_KIND_LABELS,
  DEVICE_STATUS_LABELS,
  OPERATION_MODE_LABELS,
  SENSOR_METRIC_LABELS,
} from "@/constants";
import type { ControllableMetric, OperationMode } from "@/types";
import {
  deviceStatusTone,
  describeChange,
  useZoneControl,
} from "./useZoneControl";

interface MobileZoneControlProps {
  farmId: string;
  zoneId: number;
  canControl: boolean;
}

// 온도만 손잡이 −/+로 바로 조정할 수 있게 조금 크게 잡은 스텝(시안 m2-control) — 실제 목표값은
// 서버 응답(state.setpoints)에서만 오고, 이 스텝은 그 값을 얼마씩 올릴지 정하는 UI 상수일 뿐이다.
const METRIC_STEP: Record<ControllableMetric, number> = {
  TEMPERATURE: 0.5,
  HUMIDITY: 1,
  CO2: 10,
  PPFD: 5,
};

// 모바일 제어 화면(이슈 #147, 시안 m2-control) — 데스크톱 ZoneControlPanel과 같은 useZoneControl
// 훅을 쓴다(재사용 원칙, 이번 이슈 핵심 지시). 온도만 큰 카드, 나머지 3종은 2열 요약, 장비는
// 44×26 토글, 적용/되돌리기는 화면 하단 고정 대신 콘텐츠 마지막 카드로 둔다(모바일 셸의 하단
// 탭바와 겹치는 두 개의 sticky bottom을 안전하게 쌓을 방법이 없어 — MobileTopBar.tsx 주석과
// 같은 이유로 셸 쪽에 prop-drilling하지 않는 쪽을 택했다).
export default function MobileZoneControl({
  farmId,
  zoneId,
  canControl,
}: MobileZoneControlProps) {
  const {
    state,
    loadError,
    actionError,
    infoMessage,
    conflictNotice,
    busy,
    drafts,
    setDrafts,
    handleModeChange,
    handleEnqueueSetpoint,
    handleToggleDevice,
    handleCancelChange,
    handleCancelAll,
    handleApply,
    handleEmergencyStop,
  } = useZoneControl(farmId, zoneId);

  if (loadError) {
    return <p className="px-4 py-4 text-sm text-dp-red-ink">{loadError}</p>;
  }

  if (!state) {
    return <p className="px-4 py-4 text-sm text-dp-muted">불러오는 중...</p>;
  }

  const devicesById = new Map(state.devices.map((d) => [d.id, d]));
  const temperature = state.setpoints.find((s) => s.metric === "TEMPERATURE");
  const restMetrics = CONTROLLABLE_METRICS.filter((m) => m !== "TEMPERATURE");

  function adjustDraft(metric: ControllableMetric, direction: 1 | -1) {
    const setpoint = state!.setpoints.find((s) => s.metric === metric);
    const base =
      drafts[metric] !== undefined && drafts[metric] !== ""
        ? Number(drafts[metric])
        : (setpoint?.targetValue ?? 0);
    const next =
      Math.round((base + direction * METRIC_STEP[metric]) * 100) / 100;
    setDrafts((prev) => ({ ...prev, [metric]: String(next) }));
  }

  return (
    <div className="flex flex-col gap-3 px-4 py-3">
      <div className="flex items-center gap-2.5 rounded-[9px] bg-dp-surface px-3.5 py-2.5">
        <span className="text-[12px] font-medium text-dp-sub">운전 모드</span>
        <div className="flex gap-1.5">
          {(["AUTO", "MANUAL"] as OperationMode[]).map((m) => (
            <button
              key={m}
              type="button"
              disabled={busy || !canControl}
              onClick={() => handleModeChange(m)}
              className={`min-h-11 rounded-md px-3.5 text-[12.5px] font-semibold ${
                state.mode === m
                  ? "bg-dp-ink text-dp-surface"
                  : "border border-dp-line-strong text-dp-body"
              }`}
            >
              {OPERATION_MODE_LABELS[m]}
            </button>
          ))}
        </div>
        <div className="flex-1" />
        {canControl && (
          <button
            type="button"
            disabled={busy}
            onClick={handleEmergencyStop}
            className="min-h-11 rounded-md border border-dp-red-line px-3 text-[12px] font-semibold text-dp-red-ink"
          >
            비상 정지
          </button>
        )}
      </div>

      {actionError && (
        <p className="text-[12.5px] text-dp-red-ink">{actionError}</p>
      )}
      {infoMessage && (
        <p className="text-[12.5px] text-dp-green-ink">{infoMessage}</p>
      )}
      {conflictNotice && (
        <p
          role="alert"
          className="rounded-md border border-dp-amber-line bg-dp-amber-tint px-3 py-2 text-[12px] text-dp-amber-deep"
        >
          다른 사용자가 대기 큐를 변경했습니다. 아래 목록이 최신으로
          갱신되었습니다.
        </p>
      )}

      {/* 온도 — 큰 카드(시안: 값 30px, −/+ 전폭) */}
      <div className="rounded-[10px] border border-dp-line bg-dp-surface px-4 py-[15px]">
        <span className="text-[13.5px] font-semibold text-dp-ink">
          {SENSOR_METRIC_LABELS.TEMPERATURE}
        </span>
        <div className="mt-3 text-[30px] leading-none font-bold text-dp-ink">
          {temperature?.targetValue != null
            ? `${drafts.TEMPERATURE || temperature.targetValue}${temperature.unit}`
            : "미설정"}
        </div>
        <div className="mt-3.5 flex gap-2">
          <button
            type="button"
            disabled={state.mode !== "AUTO" || !canControl || busy}
            onClick={() => adjustDraft("TEMPERATURE", -1)}
            className="min-h-11 flex-1 rounded-lg border border-dp-line-strong text-[16px] font-semibold text-dp-body disabled:opacity-40"
          >
            −
          </button>
          <button
            type="button"
            disabled={state.mode !== "AUTO" || !canControl || busy}
            onClick={() => adjustDraft("TEMPERATURE", 1)}
            className="min-h-11 flex-1 rounded-lg border border-dp-line-strong text-[16px] font-semibold text-dp-body disabled:opacity-40"
          >
            +
          </button>
        </div>
        {drafts.TEMPERATURE && (
          <button
            type="button"
            disabled={busy}
            onClick={() => handleEnqueueSetpoint("TEMPERATURE")}
            className="mt-2 min-h-11 w-full rounded-lg bg-dp-ink text-[12.5px] font-semibold text-dp-surface disabled:opacity-40"
          >
            {drafts.TEMPERATURE}
            {temperature?.unit}로 변경 예약
          </button>
        )}
      </div>

      {/* 습도·CO2·PPFD — 2열 요약 */}
      <div className="grid grid-cols-2 gap-2.5">
        {restMetrics.map((metric) => {
          const setpoint = state.setpoints.find((s) => s.metric === metric);
          const disabled = state.mode !== "AUTO" || !canControl;
          return (
            <div
              key={metric}
              className={`rounded-[10px] border border-dp-line bg-dp-surface px-3.5 py-3 ${disabled ? "opacity-60" : ""}`}
            >
              <span className="text-[12.5px] font-semibold text-dp-ink">
                {SENSOR_METRIC_LABELS[metric]}
              </span>
              <div className="mt-2.5 text-[21px] leading-none font-bold text-dp-ink">
                {setpoint?.targetValue != null
                  ? `${setpoint.targetValue}${setpoint.unit}`
                  : "미설정"}
              </div>
              <div className="mt-2.5 flex gap-1.5">
                <input
                  type="number"
                  inputMode="decimal"
                  aria-label={`${SENSOR_METRIC_LABELS[metric]} 목표값 입력`}
                  disabled={disabled || busy}
                  value={drafts[metric] ?? ""}
                  onChange={(e) =>
                    setDrafts((prev) => ({ ...prev, [metric]: e.target.value }))
                  }
                  className="min-h-11 w-full min-w-0 rounded-md border border-dp-line-strong bg-dp-canvas px-2 text-sm text-dp-ink disabled:opacity-60"
                />
                <button
                  type="button"
                  disabled={disabled || busy || !drafts[metric]?.trim()}
                  onClick={() => handleEnqueueSetpoint(metric)}
                  className="min-h-11 flex-none rounded-md bg-dp-ink px-2.5 text-xs font-medium whitespace-nowrap text-dp-surface disabled:opacity-40"
                >
                  예약
                </button>
              </div>
            </div>
          );
        })}
      </div>

      {/* 장비 — 44×26 토글(핸드오프 §1) */}
      <div className="rounded-[10px] border border-dp-line bg-dp-surface px-4 py-3.5">
        <div className="mb-2.5 flex items-baseline">
          <span className="text-[13.5px] font-semibold text-dp-ink">장비</span>
          <div className="flex-1" />
          <span className="text-[11.5px] text-dp-muted">
            {state.devices.length}대
          </span>
        </div>
        {state.devices.length === 0 ? (
          <p className="text-[12.5px] text-dp-muted">
            이 존에 등록된 장비가 없습니다.
          </p>
        ) : (
          state.devices.map((device) => {
            const controllable = device.kind === "CONTROLLER";
            const modeBlocks = state.mode !== "MANUAL";
            const on = device.status !== "OFF" && device.status !== "OFFLINE";
            const offline = device.status === "OFFLINE";
            return (
              <div
                key={device.id}
                className="flex items-center gap-3 border-b border-dp-line-row py-[11px] last:border-b-0"
              >
                <div className="min-w-0 flex-1">
                  <div className="truncate text-[13px] font-semibold text-dp-ink">
                    {device.name}
                  </div>
                  <div className="mt-0.5 text-[11.5px] text-dp-sub">
                    {DEVICE_KIND_LABELS[device.kind]} ·{" "}
                    {DEVICE_STATUS_LABELS[device.status]}
                  </div>
                </div>
                {controllable ? (
                  <button
                    type="button"
                    role="switch"
                    aria-checked={on}
                    aria-label={`${device.name} ${on ? "끄기" : "켜기"}`}
                    disabled={busy || modeBlocks || !canControl}
                    onClick={() => handleToggleDevice(device)}
                    className={`relative h-[26px] w-11 flex-none rounded-full transition-colors disabled:opacity-40 ${
                      offline
                        ? "bg-dp-red-tint"
                        : on
                          ? "bg-dp-green"
                          : "bg-dp-disabled"
                    }`}
                  >
                    <span
                      className={`absolute top-[3px] h-5 w-5 rounded-full bg-white transition-all ${on ? "left-[23px]" : "left-[3px]"}`}
                    />
                  </button>
                ) : (
                  <span className="flex-none text-[11.5px] text-dp-faint">
                    조작 대상 아님
                  </span>
                )}
              </div>
            );
          })
        )}
        {!canControl ? (
          <p className="mt-1.5 text-[11px] text-dp-faint">
            조회 전용 역할입니다.
          </p>
        ) : (
          state.mode !== "MANUAL" && (
            <p className="mt-1.5 text-[11px] text-dp-faint">
              자동 운전 중에는 장비를 직접 조작할 수 없습니다.
            </p>
          )
        )}
      </div>

      {/* 적용 대기 변경 목록 + 적용 바(시안: 되돌리기:적용 = 1:1.4) */}
      {state.pendingChanges.length > 0 && (
        <ul className="flex flex-col gap-1.5">
          {state.pendingChanges.map((change) => (
            <li
              key={change.id}
              className="flex items-center justify-between gap-2 rounded-md bg-dp-inset px-3 py-2 text-[12px] text-dp-body"
            >
              <span className="min-w-0 flex-1 truncate">
                {describeChange(change, devicesById)}
              </span>
              {canControl && (
                <button
                  type="button"
                  aria-label="이 변경 취소"
                  disabled={busy}
                  onClick={() => handleCancelChange(change.id)}
                  className="flex-none px-1 text-dp-faint disabled:opacity-60"
                >
                  ×
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      {canControl && (
        <div className="flex gap-2.5 pb-1">
          <button
            type="button"
            disabled={busy || state.pendingChanges.length === 0}
            onClick={handleCancelAll}
            className="min-h-12 flex-1 rounded-[9px] border border-dp-line-strong text-[13.5px] font-semibold text-dp-body disabled:opacity-40"
          >
            되돌리기
          </button>
          <button
            type="button"
            disabled={busy || state.pendingChanges.length === 0}
            onClick={handleApply}
            className="min-h-12 flex-[1.4] rounded-[9px] bg-dp-green text-[13.5px] font-semibold text-dp-on-green disabled:opacity-40"
          >
            {state.pendingChanges.length}건 적용
          </button>
        </div>
      )}
    </div>
  );
}
