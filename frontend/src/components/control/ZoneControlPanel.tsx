"use client";

import { Card, CardTitle, Chip, StatusBadge } from "@/components/monitoring/ui";
import SimulatedBadge from "@/components/monitoring/SimulatedBadge";
import {
  CONTROLLABLE_METRICS,
  DEVICE_KIND_LABELS,
  DEVICE_STATUS_LABELS,
  OPERATION_MODE_LABELS,
  SENSOR_METRIC_LABELS,
} from "@/constants";
import type { OperationMode } from "@/types";
import {
  deviceStatusTone,
  describeChange,
  type UseZoneControlResult,
} from "./useZoneControl";

interface ZoneControlPanelProps {
  // OPERATOR 이상(이슈 #123 — 구 OWNER 전용에서 완화. contract §2: 비상 정지·제어 조작은
  // OPERATOR 이상). 이름은 호출부(FarmControlPanel)의 hasFarmRoleAtLeast 판정 결과를 그대로 받는다.
  canControl: boolean;
  // useZoneControl 훅 결과(이슈 #148) — FarmControlPanel이 한 번만 부른 결과를 그대로 받는다.
  // 이 컴포넌트는 더 이상 훅을 직접 부르지 않는다(존 선택 1회당 요청 1건을 보장하려면 인스턴스가
  // 1개여야 하므로).
  controls: UseZoneControlResult;
}

// 존 제어 화면 본체(이슈 #108, contract §4.12) — 운전 모드 + 목표값 4종 + 장비 수동 조작 +
// 적용 대기 큐(+ CT005 재확인 플로우) + 최근 적용 이력 + 비상 정지(OPERATOR 이상, 이슈 #123).
//
// ⚠️ 대기 큐는 서버 저장이다(§4.12 — 새로고침·다중 탭·다중 사용자에 공유). 로컬 state로 흉내내지
// 않고 조회/적용 응답의 큐를 그대로 반영한다.
//
// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card·CardTitle·Chip·StatusBadge, components/monitoring/ui.tsx)를
// 재사용한다(이슈 #108 리뷰 P2 — #109 라우트 디자인 통일 전에 이 화면만 팔레트가 이탈하지 않게).
export default function ZoneControlPanel({
  canControl,
  controls,
}: ZoneControlPanelProps) {
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
  } = controls;

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
        {canControl && (
          <Chip
            as="button"
            tone="critical"
            disabled={busy}
            onClick={handleEmergencyStop}
          >
            비상 정지
          </Chip>
        )}
      </div>

      <Card className="flex items-center gap-2 px-3 py-2.5">
        <span className="text-xs font-medium text-dp-sub">운전 모드</span>
        <div className="flex gap-1.5">
          {(["AUTO", "MANUAL"] as OperationMode[]).map((m) => (
            <Chip
              key={m}
              as="button"
              size="sm"
              active={state.mode === m}
              disabled={busy || !canControl}
              onClick={() => handleModeChange(m)}
            >
              {OPERATION_MODE_LABELS[m]}
            </Chip>
          ))}
        </div>
        {!canControl && (
          <span className="text-[11px] text-dp-faint">
            조회 전용 역할입니다.
          </span>
        )}
      </Card>

      {actionError && <p className="text-sm text-dp-red-ink">{actionError}</p>}
      {infoMessage && (
        <p className="text-sm text-dp-green-ink">{infoMessage}</p>
      )}

      {/* 목표값 4종 — MANUAL에서는 편집 거부(CT003)라 비활성 처리로 사전 차단한다 */}
      <div>
        <h4 className="mb-2">
          <CardTitle>목표값</CardTitle>
        </h4>
        <div className="grid grid-cols-1 gap-3 min-[640px]:grid-cols-2 min-[1200px]:grid-cols-4">
          {CONTROLLABLE_METRICS.map((metric) => {
            const setpoint = state.setpoints.find((s) => s.metric === metric);
            const disabled = state.mode !== "AUTO" || !canControl;
            return (
              <Card
                key={metric}
                className={`p-3 ${disabled ? "opacity-50" : ""}`}
              >
                <div className="text-xs font-medium text-dp-sub">
                  {SENSOR_METRIC_LABELS[metric]}
                </div>
                <div className="mt-1 text-lg font-bold text-dp-ink">
                  {setpoint?.targetValue != null
                    ? `${setpoint.targetValue}${setpoint.unit}`
                    : "미설정"}
                </div>
                <div className="mt-2 flex gap-1.5">
                  <input
                    type="number"
                    inputMode="decimal"
                    aria-label={`${SENSOR_METRIC_LABELS[metric]} 목표값 입력`}
                    disabled={disabled || busy}
                    value={drafts[metric] ?? ""}
                    onChange={(e) =>
                      setDrafts((prev) => ({
                        ...prev,
                        [metric]: e.target.value,
                      }))
                    }
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
                {!canControl ? (
                  <p className="mt-1.5 text-[11px] text-dp-faint">
                    조회 전용 역할입니다.
                  </p>
                ) : (
                  disabled && (
                    <p className="mt-1.5 text-[11px] text-dp-faint">
                      수동 운전에서는 편집할 수 없습니다.
                    </p>
                  )
                )}
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
          <p className="text-sm text-dp-muted">
            이 존에 등록된 장비가 없습니다.
          </p>
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
                    <div className="truncate text-sm font-medium text-dp-ink">
                      {device.name}
                    </div>
                    <div className="mt-1 flex items-center gap-1.5 text-xs text-dp-sub">
                      <span>{DEVICE_KIND_LABELS[device.kind]}</span>
                      <StatusBadge
                        label={DEVICE_STATUS_LABELS[device.status]}
                        tone={deviceStatusTone(device.status)}
                      />
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
                      className={`flex-none rounded-full px-3 py-1 text-xs font-semibold transition-colors disabled:opacity-40 ${
                        on
                          ? "bg-dp-green-tint-2 text-dp-green-ink"
                          : "bg-dp-badge-neutral text-dp-muted"
                      }`}
                    >
                      {offline ? "통신두절" : on ? "켜짐" : "꺼짐"}
                    </button>
                  ) : (
                    <span className="flex-none text-xs text-dp-faint">
                      조작 대상 아님
                    </span>
                  )}
                </Card>
              );
            })}
          </div>
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

      {/* 적용 대기 변경 — 서버 저장 큐를 그대로 반영(§4.12). CT005 발생 시 아래 배너 + 갱신된 목록으로 재확인시킨다. */}
      <Card className="p-3">
        <div className="mb-2 flex items-center justify-between">
          <CardTitle>적용 대기 변경</CardTitle>
          {canControl && state.pendingChanges.length > 0 && (
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
            다른 사용자가 대기 큐를 변경했습니다. 아래 목록이 최신 상태로
            갱신되었습니다 — 확인 후 다시 &ldquo;적용&rdquo;을 눌러주세요.
          </p>
        )}

        {state.pendingChanges.length === 0 ? (
          <p className="rounded-md bg-dp-inset px-3 py-3 text-xs text-dp-sub">
            대기 중인 변경이 없습니다. 목표값을 조정하거나 장비를 조작하면
            여기에 쌓입니다.
          </p>
        ) : (
          <ul className="flex flex-col gap-1.5">
            {state.pendingChanges.map((change) => (
              <li
                key={change.id}
                className="flex items-center justify-between gap-2 rounded-md bg-dp-inset px-3 py-2 text-xs text-dp-body"
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
                    className="flex-none text-dp-faint hover:text-dp-ink disabled:opacity-60"
                  >
                    ×
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}

        {canControl && (
          <button
            type="button"
            disabled={busy || state.pendingChanges.length === 0}
            onClick={handleApply}
            className="mt-3 w-full rounded-md bg-dp-ink py-2 text-sm font-semibold text-dp-surface disabled:opacity-40"
          >
            {state.pendingChanges.length}건 적용
          </button>
        )}
      </Card>

      {state.recentApplyLogs.length > 0 && (
        <div>
          <h4 className="mb-2">
            <CardTitle>최근 적용 이력</CardTitle>
          </h4>
          <ul className="flex flex-col gap-1 text-xs text-dp-muted">
            {state.recentApplyLogs.map((log) => (
              <li key={log.id}>
                <span className="font-mono">
                  {new Date(log.appliedAt).toLocaleString("ko-KR")}
                </span>{" "}
                · {log.summary}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
