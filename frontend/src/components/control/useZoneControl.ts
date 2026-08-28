"use client";

import { useCallback, useEffect, useState } from "react";
import {
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

// 존 제어 상태·조작(이슈 #108, contract §4.12)을 데스크톱(ZoneControlPanel)·모바일
// (MobileZoneControl, 이슈 #147) 두 화면이 공유하는 훅으로 뺐다. 화면마다 새로 fetch/조작
// 로직을 만들면 한쪽만 고쳐지는 위험이 있어(핸드오프 원칙), 상태·핸들러는 여기 하나뿐이고
// 두 화면은 렌더링만 다르다. 동작(큐·CT005 재확인 플로우·비상정지 등)은 원본 그대로다.
export function useZoneControl(farmId: string, zoneId: number) {
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
        `운전 모드를 ${OPERATION_MODE_LABELS[next]}(으)로 변경하면 새 모드에서 허용되지 않는 대기 항목이 폐기됩니다. 계속하시겠습니까?`,
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
      await enqueueControlChange(farmId, zoneId, {
        kind: "SETPOINT",
        metric,
        targetValue: value,
      });
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
    const targetStatus: Extract<DeviceStatus, "NORMAL" | "OFF"> =
      device.status === "OFF" ? "NORMAL" : "OFF";
    setBusy(true);
    setActionError(null);
    try {
      await enqueueControlChange(farmId, zoneId, {
        kind: "DEVICE",
        deviceId: device.id,
        targetStatus,
      });
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
    if (
      !window.confirm(
        `대기 중인 변경 ${state.pendingChanges.length}건을 모두 되돌리시겠습니까?`,
      )
    )
      return;
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
          : `${res.appliedCount}건 적용되었습니다.`,
      );
    } catch (err) {
      if (isQueueConflict(err)) {
        setState((prev) =>
          prev ? { ...prev, pendingChanges: err.data.pendingChanges } : prev,
        );
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
        "비상 정지를 실행하시겠습니까? 농장 전체 제어기가 즉시 꺼지고 모든 존이 수동 운전으로 전환되며, 대기 중인 변경은 전부 폐기됩니다.\n(센서·통신 장치는 계속 측정합니다 — 전체 정지가 아닙니다)",
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
        `비상 정지 완료 — 존 ${res.zoneCount}개, 제어기 ${res.stoppedDeviceCount}대 정지, 대기 항목 ${res.discardedChangeCount}건 폐기`,
      );
      reload();
    } catch (err) {
      setActionError(resolveErrorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return {
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
  };
}

// 장비 상태 → StatusBadge 톤. OFF는 장애가 아니라 제어 조작 결과라 critical이 아니라
// neutral로 표기한다(contract §4.12, 이슈 #108 요건 ①과 같은 이유).
export type DeviceStatusTone = "done" | "warning" | "neutral" | "critical";

export function deviceStatusTone(status: DeviceStatus): DeviceStatusTone {
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
export function describeChange(
  change: ControlChangeResponse,
  devicesById: Map<number, ControlDeviceResponse>,
): string {
  if (change.kind === "SETPOINT") {
    const label = change.metric ? SENSOR_METRIC_LABELS[change.metric] : "지표";
    const unit = change.unit ?? "";
    const from = change.fromValue ?? "미설정";
    return `${label} ${from}${unit} → ${change.toValue}${unit}`;
  }
  const device =
    change.deviceId != null ? devicesById.get(change.deviceId) : undefined;
  const from = change.fromValue as DeviceStatus | null;
  const to = change.toValue as DeviceStatus;
  const fromLabel = from ? (DEVICE_STATUS_LABELS[from] ?? from) : "?";
  const toLabel = DEVICE_STATUS_LABELS[to] ?? to;
  return `${device?.name ?? "장비"} ${fromLabel} → ${toLabel}`;
}
