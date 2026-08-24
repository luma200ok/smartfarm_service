"use client";

import { useCallback, useEffect, useState } from "react";
import { Card, CardTitle, Chip, StatusBadge } from "@/components/monitoring/ui";
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

// StatusBadge(monitoring/ui.tsx)의 tone 인자와 구조적으로 일치하는 로컬 타입 — ui.tsx가
// PreviewSeverity에 "neutral"을 얹어 자체 확장했으므로(design-preview의 완전성 매핑을 깨지
// 않기 위해 export는 하지 않는다), 여기서도 같은 리터럴 집합을 로컬로 선언해 맞춘다.
type DeviceStatusTone = "done" | "warning" | "neutral" | "critical";

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
//
// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card·CardTitle·Chip·StatusBadge, components/monitoring/ui.tsx)를
// 재사용한다(이슈 #108 리뷰 P2 — #109 라우트 디자인 통일 전에 이 화면만 팔레트가 이탈하지 않게).
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
      // skippedCount는 적용 시점에 대상(존·장비)이 사라져 캐스케이드로 건너뛴 항목 수다
      // (contract §4.12) — 정상 케이스에선 0이지만, 0이 아니면 반드시 알려야 "적용했는데 왜
      // 일부가 반영 안 됐는지" 사용자가 알 수 있다(리뷰 P3).
      setInfoMessage(
        res.skippedCount > 0
          ? `${res.appliedCount}건 적용, ${res.skippedCount}건은 적용 시점에 대상이 사라져 건너뛰었습니다.`
          : `${res.appliedCount}건 적용되었습니다.`
      );
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
    return <p className="text-sm text-dp-red-ink">{loadError}</p>;
  }

  if (!state) {
    return <p className="text-sm text-dp-muted">불러오는 중...</p>;
  }

  const devicesById = new Map(state.devices.map((d) => [d.id, d]));

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-2.5">
        <CardTitle size="lg">{state.zoneName}</CardTitle>
        <SimulatedBadge simulated={state.simulated} />
        <div className="flex-1" />
        {isOwner && (
          <Chip as="button" tone="critical" disabled={busy} onClick={handleEmergencyStop}>
            비상 정지
          </Chip>
        )}
      </div>

      <Card className="flex items-center gap-2 px-3 py-2.5">
        <span className="text-xs font-medium text-dp-sub">운전 모드</span>
        <div className="flex gap-1.5">
          {(["AUTO", "MANUAL"] as OperationMode[]).map((m) => (
            <Chip key={m} as="button" size="sm" active={state.mode === m} disabled={busy} onClick={() => handleModeChange(m)}>
              {OPERATION_MODE_LABELS[m]}
            </Chip>
          ))}
        </div>
      </Card>

      {actionError && <p className="text-sm text-dp-red-ink">{actionError}</p>}
      {infoMessage && <p className="text-sm text-dp-green-ink">{infoMessage}</p>}

      {/* 목표값 4종 — MANUAL에서는 편집 거부(CT003)라 비활성 처리로 사전 차단한다 */}
      <div>
        <h4 className="mb-2">
          <CardTitle>목표값</CardTitle>
        </h4>
        <div className="grid grid-cols-1 gap-3 min-[640px]:grid-cols-2 min-[1200px]:grid-cols-4">
          {CONTROLLABLE_METRICS.map((metric) => {
            const setpoint = state.setpoints.find((s) => s.metric === metric);
            const disabled = state.mode !== "AUTO";
            return (
              <Card key={metric} className={`p-3 ${disabled ? "opacity-50" : ""}`}>
                <div className="text-xs font-medium text-dp-sub">{SENSOR_METRIC_LABELS[metric]}</div>
                <div className="mt-1 text-lg font-bold text-dp-ink">
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
                    className="w-full min-w-0 rounded-md border border-dp-line-strong bg-dp-surface px-2 py-1 text-sm text-dp-ink disabled:opacity-60"
                  />
                  <button
                    type="button"
                    disabled={disabled || busy || !drafts[metric]?.trim()}
                    onClick={() => handleEnqueueSetpoint(metric)}
                    className="flex-none rounded-md bg-dp-ink px-2.5 py-1 text-xs font-medium whitespace-nowrap text-dp-surface disabled:opacity-40"
                  >
                    변경 예약
                  </button>
                </div>
                {disabled && <p className="mt-1.5 text-[11px] text-dp-faint">수동 운전에서는 편집할 수 없습니다.</p>}
              </Card>
            );
          })}
        </div>
      </div>

      {/* 장비 수동 조작 — AUTO에서는 토글 거부(CT003)라 비활성 처리로 사전 차단한다 */}
      <div>
        <h4 className="mb-2">
          <CardTitle>장비 수동 조작</CardTitle>
        </h4>
        {state.devices.length === 0 ? (
          <p className="text-sm text-dp-muted">이 존에 등록된 장비가 없습니다.</p>
        ) : (
          <div className="grid grid-cols-1 gap-2 min-[768px]:grid-cols-2">
            {state.devices.map((device) => {
              const controllable = device.kind === "CONTROLLER";
              const modeBlocks = state.mode !== "MANUAL";
              const on = device.status !== "OFF" && device.status !== "OFFLINE";
              const offline = device.status === "OFFLINE";
              return (
                <Card
                  key={device.id}
                  className={`flex items-center justify-between gap-3 px-3 py-2 ${offline ? "!border-dp-red-line !bg-dp-red-tint" : ""}`}
                >
                  <div className="min-w-0">
                    <div className="truncate text-sm font-medium text-dp-ink">{device.name}</div>
                    <div className="mt-1 flex items-center gap-1.5 text-xs text-dp-sub">
                      <span>{DEVICE_KIND_LABELS[device.kind]}</span>
                      <StatusBadge label={DEVICE_STATUS_LABELS[device.status]} tone={deviceStatusTone(device.status)} />
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
                        on ? "bg-dp-green-tint-2 text-dp-green-ink" : "bg-dp-badge-neutral text-dp-muted"
                      }`}
                    >
                      {offline ? "통신두절" : on ? "켜짐" : "꺼짐"}
                    </button>
                  ) : (
                    <span className="flex-none text-xs text-dp-faint">조작 대상 아님</span>
                  )}
                </Card>
              );
            })}
          </div>
        )}
        {state.mode !== "MANUAL" && (
          <p className="mt-1.5 text-[11px] text-dp-faint">자동 운전 중에는 장비를 직접 조작할 수 없습니다.</p>
        )}
      </div>

      {/* 적용 대기 변경 — 서버 저장 큐를 그대로 반영(§4.12). CT005 발생 시 아래 배너 + 갱신된 목록으로 재확인시킨다. */}
      <Card className="p-3">
        <div className="mb-2 flex items-center justify-between">
          <CardTitle>적용 대기 변경</CardTitle>
          {state.pendingChanges.length > 0 && (
            <button
              type="button"
              disabled={busy}
              onClick={handleCancelAll}
              className="text-xs text-dp-sub hover:text-dp-ink hover:underline disabled:opacity-60"
            >
              전체 되돌리기
            </button>
          )}
        </div>

        {conflictNotice && (
          <p
            role="alert"
            className="mb-2.5 rounded-md border border-dp-amber-line bg-dp-amber-tint px-3 py-2 text-xs leading-relaxed text-dp-amber-deep"
          >
            다른 사용자가 대기 큐를 변경했습니다. 아래 목록이 최신 상태로 갱신되었습니다 — 확인 후 다시
            &ldquo;적용&rdquo;을 눌러주세요.
          </p>
        )}

        {state.pendingChanges.length === 0 ? (
          <p className="rounded-md bg-dp-inset px-3 py-3 text-xs text-dp-sub">
            대기 중인 변경이 없습니다. 목표값을 조정하거나 장비를 조작하면 여기에 쌓입니다.
          </p>
        ) : (
          <ul className="flex flex-col gap-1.5">
            {state.pendingChanges.map((change) => (
              <li
                key={change.id}
                className="flex items-center justify-between gap-2 rounded-md bg-dp-inset px-3 py-2 text-xs text-dp-body"
              >
                <span className="min-w-0 flex-1 truncate">{describeChange(change, devicesById)}</span>
                <button
                  type="button"
                  aria-label="이 변경 취소"
                  disabled={busy}
                  onClick={() => handleCancelChange(change.id)}
                  className="flex-none text-dp-faint hover:text-dp-ink disabled:opacity-60"
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
          className="mt-3 w-full rounded-md bg-dp-ink py-2 text-sm font-semibold text-dp-surface disabled:opacity-40"
        >
          {state.pendingChanges.length}건 적용
        </button>
      </Card>

      {state.recentApplyLogs.length > 0 && (
        <div>
          <h4 className="mb-2">
            <CardTitle>최근 적용 이력</CardTitle>
          </h4>
          <ul className="flex flex-col gap-1 text-xs text-dp-muted">
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

// 장비 상태 → StatusBadge 톤. OFF는 장애가 아니라 제어 조작 결과라 critical이 아니라
// neutral로 표기한다(contract §4.12, 이슈 #108 요건 ①과 같은 이유).
function deviceStatusTone(status: DeviceStatus): DeviceStatusTone {
  switch (status) {
    case "NORMAL":
      return "done";
    case "WARNING":
      return "warning";
    case "OFF":
      return "neutral";
    default:
      return "critical";
  }
}

// 적용 대기 항목 1건을 사람이 읽는 문구로 — SETPOINT는 값+단위, DEVICE는 상태 라벨(백엔드가
// enum name 문자열로 fromValue/toValue를 싣는다, ControlService#buildDeviceChange 참고).
// 라벨 조회는 예기치 않은 문자열이 와도 undefined 대신 원문을 보여준다(리뷰 P3 — 런타임 검증
// 없는 캐스트라 백엔드가 새 enum 값을 추가하면 화면이 깨질 수 있었다).
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
  const fromLabel = from ? (DEVICE_STATUS_LABELS[from] ?? from) : "?";
  const toLabel = DEVICE_STATUS_LABELS[to] ?? to;
  return `${device?.name ?? "장비"} ${fromLabel} → ${toLabel}`;
}
