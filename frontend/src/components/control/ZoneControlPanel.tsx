"use client";

import { useCallback, useEffect, useState } from "react";
import SimulatedBadge from "@/components/monitoring/SimulatedBadge";
import {
  CONTROLLABLE_METRICS,
  DEVICE_KIND_LABELS,
  DEVICE_STATUS_LABELS,
  ERROR_MESSAGES,
  OPERATION_MODE_LABELS,
  SENSOR_METRIC_LABELS,
} from "@/constants";
import {
  applyControlChanges,
  cancelAllControlChanges,
  cancelControlChange,
  changeControlMode,
  emergencyStop,
  enqueueControlChange,
  getControlState,
} from "@/lib/api/control";
import { isQueueConflict, resolveErrorMessage } from "@/lib/api/errorMessage";
import type {
  ControllableMetric,
  ControlChangeResponse,
  ControlDeviceResponse,
  ControlStateResponse,
  DeviceStatus,
  OperationMode,
} from "@/types";

interface ZoneControlPanelProps {
  farmId: string;
  zoneId: number;
  isOwner: boolean;
}

// 존 제어 화면 본체(이슈 #108, contract §4.12) — 운전 모드 + 목표값 4종 + 장비 수동 조작 +
// 적용 대기 큐(+ CT005 재확인 플로우) + 최근 적용 이력 + 비상 정지(OWNER).
//
// ⚠️ 대기 큐는 서버 저장이다(§4.12 — 새로고침·다중 탭·다중 사용자에 공유). 로컬 state로 흉내내지
// 않고 조회/적용 응답의 큐를 그대로 반영한다.
export default function ZoneControlPanel({ farmId, zoneId, isOwner }: ZoneControlPanelProps) {
  const [state, setState] = useState<ControlStateResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [infoMessage, setInfoMessage] = useState<string | null>(null);
  const [conflictNotice, setConflictNotice] = useState(false);
  const [busy, setBusy] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [drafts, setDrafts] = useState<Record<string, string>>({});

  const reload = useCallback(() => setRefreshKey((k) => k + 1), []);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoadError(null);
      try {
        const res = await getControlState(farmId, zoneId);
        if (!cancelled) {
          setState(res);
          setDrafts({});
        }
      } catch (err) {
        if (!cancelled) setLoadError(resolveErrorMessage(err));
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [farmId, zoneId, refreshKey]);

  async function handleModeChange(next: OperationMode) {
    if (!state || busy || state.mode === next) return;
    if (
      state.pendingChanges.length > 0 &&
      !window.confirm(
        `운전 모드를 ${OPERATION_MODE_LABELS[next]}(으)로 변경하면 새 모드에서 허용되지 않는 대기 항목이 폐기됩니다. 계속하시겠습니까?`
      )
    ) {
      return;
    }
    setBusy(true);
    setActionError(null);
    try {
      const res = await changeControlMode(farmId, zoneId, { mode: next });
      setState(res);
      setConflictNotice(false);
      setInfoMessage(null);
    } catch (err) {
      setActionError(resolveErrorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleEnqueueSetpoint(metric: ControllableMetric) {
    if (!state || busy) return;
    const raw = drafts[metric];
    if (raw === undefined || raw.trim() === "") return;
    const value = Number(raw);
    if (!Number.isFinite(value)) {
      setActionError("숫자를 입력해주세요.");
      return;
    }
    setBusy(true);
    setActionError(null);
    try {
      await enqueueControlChange(farmId, zoneId, { kind: "SETPOINT", metric, targetValue: value });
      setDrafts((prev) => ({ ...prev, [metric]: "" }));
      setInfoMessage(null);
      reload();
    } catch (err) {
      setActionError(resolveErrorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleToggleDevice(device: ControlDeviceResponse) {
    if (!state || busy) return;
    // 통신 두절 장비는 큐 적재 시점에 CT002로 거부된다(contract §4.12) — 클릭 즉시 안내하고
    // 서버 왕복 없이 끝낸다(적용 시점이 아니라 클릭 시점 피드백이 요건).
    if (device.status === "OFFLINE") {
      setActionError(ERROR_MESSAGES.CT002);
      return;
    }
    const targetStatus: Extract<DeviceStatus, "NORMAL" | "OFF"> = device.status === "OFF" ? "NORMAL" : "OFF";
    setBusy(true);
    setActionError(null);
    try {
      await enqueueControlChange(farmId, zoneId, { kind: "DEVICE", deviceId: device.id, targetStatus });
      setInfoMessage(null);
      reload();
    } catch (err) {
      setActionError(resolveErrorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleCancelChange(changeId: number) {
    if (busy) return;
    setBusy(true);
    setActionError(null);
    try {
      await cancelControlChange(farmId, zoneId, changeId);
      reload();
    } catch (err) {
      setActionError(resolveErrorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleCancelAll() {
    if (!state || state.pendingChanges.length === 0 || busy) return;
    if (!window.confirm(`대기 중인 변경 ${state.pendingChanges.length}건을 모두 되돌리시겠습니까?`)) return;
    setBusy(true);
    setActionError(null);
    try {
      await cancelAllControlChanges(farmId, zoneId);
      setConflictNotice(false);
      reload();
    } catch (err) {
      setActionError(resolveErrorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  // CT005 재확인 플로우(이슈 #108 핵심) — apply가 409로 거부되면 에러만 띄우지 않고 응답에 실린
  // 최신 pendingChanges로 화면 큐를 그 자리에서 갱신한다. expectedChangeIds는 항상 "지금 화면에
  // 보이는 큐"에서 계산하므로, 사용자가 갱신된 목록을 보고 다시 "적용"을 누르면 그 최신 집합으로
  // 재시도된다 — 별도 재조회 없이 재확인이 자연스럽게 이어진다.
  async function handleApply() {
    if (!state || state.pendingChanges.length === 0 || busy) return;
    setBusy(true);
    setActionError(null);
    setInfoMessage(null);
    try {
      const res = await applyControlChanges(farmId, zoneId, {
        expectedChangeIds: state.pendingChanges.map((c) => c.id),
      });
      setState(res.state);
      setConflictNotice(false);
      setInfoMessage(`${res.appliedCount}건 적용되었습니다.`);
    } catch (err) {
      if (isQueueConflict(err)) {
        setState((prev) => (prev ? { ...prev, pendingChanges: err.data.pendingChanges } : prev));
        setConflictNotice(true);
      } else {
        setActionError(resolveErrorMessage(err));
      }
    } finally {
      setBusy(false);
    }
  }

  async function handleEmergencyStop() {
    if (busy) return;
    if (
      !window.confirm(
        "비상 정지를 실행하시겠습니까? 농장 전체 제어기가 즉시 꺼지고 모든 존이 수동 운전으로 전환되며, 대기 중인 변경은 전부 폐기됩니다.\n(센서·통신 장치는 계속 측정합니다 — 전체 정지가 아닙니다)"
      )
    ) {
      return;
    }
    setBusy(true);
    setActionError(null);
    try {
      const res = await emergencyStop(farmId);
      setConflictNotice(false);
      setInfoMessage(
        `비상 정지 완료 — 존 ${res.zoneCount}개, 제어기 ${res.stoppedDeviceCount}대 정지, 대기 항목 ${res.discardedChangeCount}건 폐기`
      );
      reload();
    } catch (err) {
      setActionError(resolveErrorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  if (loadError) {
    return <p className="text-sm text-red-600 dark:text-red-400">{loadError}</p>;
  }

  if (!state) {
    return <p className="text-sm text-zinc-500 dark:text-zinc-400">불러오는 중...</p>;
  }

  const devicesById = new Map(state.devices.map((d) => [d.id, d]));

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-2.5">
        <h3 className="text-base font-semibold text-zinc-900 dark:text-zinc-50">{state.zoneName}</h3>
        <SimulatedBadge simulated={state.simulated} />
        <div className="flex-1" />
        {isOwner && (
          <button
            type="button"
            disabled={busy}
            onClick={handleEmergencyStop}
            className="rounded-md border border-red-300 px-3 py-1.5 text-xs font-semibold text-red-600 transition-colors hover:bg-red-50 disabled:opacity-60 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-950"
          >
            비상 정지
          </button>
        )}
      </div>

      <div className="flex items-center gap-2 rounded-md border border-zinc-200 px-3 py-2 dark:border-zinc-800">
        <span className="text-xs font-medium text-zinc-500 dark:text-zinc-400">운전 모드</span>
        <div className="flex gap-1.5">
          {(["AUTO", "MANUAL"] as OperationMode[]).map((m) => (
            <button
              key={m}
              type="button"
              aria-pressed={state.mode === m}
              disabled={busy}
              onClick={() => handleModeChange(m)}
              className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                state.mode === m
                  ? "bg-zinc-900 text-white dark:bg-zinc-50 dark:text-zinc-900"
                  : "border border-zinc-300 text-zinc-600 dark:border-zinc-700 dark:text-zinc-400"
              }`}
            >
              {OPERATION_MODE_LABELS[m]}
            </button>
          ))}
        </div>
      </div>

      {actionError && <p className="text-sm text-red-600 dark:text-red-400">{actionError}</p>}
      {infoMessage && <p className="text-sm text-emerald-600 dark:text-emerald-400">{infoMessage}</p>}

      {/* 목표값 4종 — MANUAL에서는 편집 거부(CT003)라 비활성 처리로 사전 차단한다 */}
      <div>
        <h4 className="mb-2 text-sm font-semibold text-zinc-900 dark:text-zinc-50">목표값</h4>
        <div className="grid grid-cols-1 gap-3 min-[640px]:grid-cols-2 min-[1200px]:grid-cols-4">
          {CONTROLLABLE_METRICS.map((metric) => {
            const setpoint = state.setpoints.find((s) => s.metric === metric);
            const disabled = state.mode !== "AUTO";
            return (
              <div
                key={metric}
                className={`rounded-md border border-zinc-200 p-3 dark:border-zinc-800 ${disabled ? "opacity-50" : ""}`}
              >
                <div className="text-xs font-medium text-zinc-500 dark:text-zinc-400">
                  {SENSOR_METRIC_LABELS[metric]}
                </div>
                <div className="mt-1 text-lg font-bold text-zinc-900 dark:text-zinc-50">
                  {setpoint?.targetValue != null ? `${setpoint.targetValue}${setpoint.unit}` : "미설정"}
                </div>
                <div className="mt-2 flex gap-1.5">
                  <input
                    type="number"
                    inputMode="decimal"
                    aria-label={`${SENSOR_METRIC_LABELS[metric]} 목표값 입력`}
                    disabled={disabled || busy}
                    value={drafts[metric] ?? ""}
                    onChange={(e) => setDrafts((prev) => ({ ...prev, [metric]: e.target.value }))}
                    className="w-full min-w-0 rounded-md border border-zinc-300 px-2 py-1 text-sm text-zinc-900 disabled:opacity-60 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50"
                  />
                  <button
                    type="button"
                    disabled={disabled || busy || !drafts[metric]?.trim()}
                    onClick={() => handleEnqueueSetpoint(metric)}
                    className="flex-none rounded-md bg-zinc-900 px-2.5 py-1 text-xs font-medium whitespace-nowrap text-white disabled:opacity-40 dark:bg-zinc-50 dark:text-zinc-900"
                  >
                    변경 예약
                  </button>
                </div>
                {disabled && (
                  <p className="mt-1.5 text-[11px] text-zinc-400 dark:text-zinc-500">
                    수동 운전에서는 편집할 수 없습니다.
                  </p>
                )}
              </div>
            );
          })}
        </div>
      </div>

      {/* 장비 수동 조작 — AUTO에서는 토글 거부(CT003)라 비활성 처리로 사전 차단한다 */}
      <div>
        <h4 className="mb-2 text-sm font-semibold text-zinc-900 dark:text-zinc-50">장비 수동 조작</h4>
        {state.devices.length === 0 ? (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">이 존에 등록된 장비가 없습니다.</p>
        ) : (
          <div className="grid grid-cols-1 gap-2 min-[768px]:grid-cols-2">
            {state.devices.map((device) => {
              const controllable = device.kind === "CONTROLLER";
              const modeBlocks = state.mode !== "MANUAL";
              const on = device.status !== "OFF" && device.status !== "OFFLINE";
              const offline = device.status === "OFFLINE";
              return (
                <div
                  key={device.id}
                  className={`flex items-center justify-between gap-3 rounded-md border px-3 py-2 ${
                    offline
                      ? "border-red-300 bg-red-50 dark:border-red-900 dark:bg-red-950"
                      : "border-zinc-200 dark:border-zinc-800"
                  }`}
                >
                  <div className="min-w-0">
                    <div className="truncate text-sm font-medium text-zinc-900 dark:text-zinc-50">{device.name}</div>
                    <div className="text-xs text-zinc-500 dark:text-zinc-400">
                      {DEVICE_KIND_LABELS[device.kind]} · {DEVICE_STATUS_LABELS[device.status]}
                    </div>
                  </div>
                  {controllable ? (
                    <button
                      type="button"
                      role="switch"
                      aria-checked={on}
                      aria-label={`${device.name} ${on ? "끄기" : "켜기"}`}
                      disabled={busy || modeBlocks}
                      onClick={() => handleToggleDevice(device)}
                      className={`flex-none rounded-full px-3 py-1 text-xs font-semibold transition-colors disabled:opacity-40 ${
                        on
                          ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-400"
                          : "bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400"
                      }`}
                    >
                      {offline ? "통신두절" : on ? "켜짐" : "꺼짐"}
                    </button>
                  ) : (
                    <span className="flex-none text-xs text-zinc-400 dark:text-zinc-500">조작 대상 아님</span>
                  )}
                </div>
              );
            })}
          </div>
        )}
        {state.mode !== "MANUAL" && (
          <p className="mt-1.5 text-[11px] text-zinc-400 dark:text-zinc-500">
            자동 운전 중에는 장비를 직접 조작할 수 없습니다.
          </p>
        )}
      </div>

      {/* 적용 대기 변경 — 서버 저장 큐를 그대로 반영(§4.12). CT005 발생 시 아래 배너 + 갱신된 목록으로 재확인시킨다. */}
      <div className="rounded-md border border-zinc-200 p-3 dark:border-zinc-800">
        <div className="mb-2 flex items-center justify-between">
          <h4 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">적용 대기 변경</h4>
          {state.pendingChanges.length > 0 && (
            <button
              type="button"
              disabled={busy}
              onClick={handleCancelAll}
              className="text-xs text-zinc-500 hover:underline disabled:opacity-60 dark:text-zinc-400"
            >
              전체 되돌리기
            </button>
          )}
        </div>

        {conflictNotice && (
          <p
            role="alert"
            className="mb-2.5 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-xs leading-relaxed text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-300"
          >
            다른 사용자가 대기 큐를 변경했습니다. 아래 목록이 최신 상태로 갱신되었습니다 — 확인 후 다시
            &ldquo;적용&rdquo;을 눌러주세요.
          </p>
        )}

        {state.pendingChanges.length === 0 ? (
          <p className="rounded-md bg-zinc-50 px-3 py-3 text-xs text-zinc-500 dark:bg-zinc-900 dark:text-zinc-400">
            대기 중인 변경이 없습니다. 목표값을 조정하거나 장비를 조작하면 여기에 쌓입니다.
          </p>
        ) : (
          <ul className="flex flex-col gap-1.5">
            {state.pendingChanges.map((change) => (
              <li
                key={change.id}
                className="flex items-center justify-between gap-2 rounded-md bg-zinc-50 px-3 py-2 text-xs text-zinc-700 dark:bg-zinc-900 dark:text-zinc-300"
              >
                <span className="min-w-0 flex-1 truncate">{describeChange(change, devicesById)}</span>
                <button
                  type="button"
                  aria-label="이 변경 취소"
                  disabled={busy}
                  onClick={() => handleCancelChange(change.id)}
                  className="flex-none text-zinc-400 hover:text-zinc-900 disabled:opacity-60 dark:hover:text-zinc-50"
                >
                  ×
                </button>
              </li>
            ))}
          </ul>
        )}

        <button
          type="button"
          disabled={busy || state.pendingChanges.length === 0}
          onClick={handleApply}
          className="mt-3 w-full rounded-md bg-zinc-900 py-2 text-sm font-semibold text-white disabled:opacity-40 dark:bg-zinc-50 dark:text-zinc-900"
        >
          {state.pendingChanges.length}건 적용
        </button>
      </div>

      {state.recentApplyLogs.length > 0 && (
        <div>
          <h4 className="mb-2 text-sm font-semibold text-zinc-900 dark:text-zinc-50">최근 적용 이력</h4>
          <ul className="flex flex-col gap-1 text-xs text-zinc-500 dark:text-zinc-400">
            {state.recentApplyLogs.map((log) => (
              <li key={log.id}>
                <span className="font-mono">{new Date(log.appliedAt).toLocaleString("ko-KR")}</span> · {log.summary}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

// 적용 대기 항목 1건을 사람이 읽는 문구로 — SETPOINT는 값+단위, DEVICE는 상태 라벨(백엔드가
// enum name 문자열로 fromValue/toValue를 싣는다, ControlService#buildDeviceChange 참고).
function describeChange(change: ControlChangeResponse, devicesById: Map<number, ControlDeviceResponse>): string {
  if (change.kind === "SETPOINT") {
    const label = change.metric ? SENSOR_METRIC_LABELS[change.metric] : "지표";
    const unit = change.unit ?? "";
    const from = change.fromValue ?? "미설정";
    return `${label} ${from}${unit} → ${change.toValue}${unit}`;
  }
  const device = change.deviceId != null ? devicesById.get(change.deviceId) : undefined;
  const from = change.fromValue as DeviceStatus | null;
  const to = change.toValue as DeviceStatus;
  return `${device?.name ?? "장비"} ${from ? DEVICE_STATUS_LABELS[from] : "?"} → ${DEVICE_STATUS_LABELS[to]}`;
}
