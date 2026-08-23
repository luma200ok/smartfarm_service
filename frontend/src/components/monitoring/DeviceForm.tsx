"use client";

import { useState, type FormEvent } from "react";
import FormField from "@/components/ui/FormField";
import { DEVICE_KIND_LABELS, DEVICE_STATUS_LABELS, SENSOR_METRIC_LABELS } from "@/constants";
import type { DeviceKind, DeviceRequest, DeviceResponse, DeviceStatus, SensorMetric, ZoneTreeResponse } from "@/types";

const KIND_OPTIONS: DeviceKind[] = ["SENSOR", "CONTROLLER", "GATEWAY"];
const STATUS_OPTIONS: DeviceStatus[] = ["NORMAL", "WARNING", "FAULT", "OFFLINE"];
const METRIC_OPTIONS: SensorMetric[] = ["TEMPERATURE", "HUMIDITY", "CO2", "EC", "PH", "PPFD", "POWER"];

interface DeviceFormProps {
  tree: ZoneTreeResponse;
  initial?: DeviceResponse;
  submitting: boolean;
  error: string | null;
  submitLabel: string;
  onSubmit: (payload: DeviceRequest) => void;
  onCancel: () => void;
}

// 수정 폼 초기값 계산 — 장비의 가장 깊은 위치 FK(rackLevelId → rackId → zoneId 순)로부터
// 존·랙 셀렉트의 초기 선택값을 역산한다(카스케이딩 드롭다운이라 선택 경로가 필요).
function resolveInitialLocation(
  initial: DeviceResponse | undefined,
  tree: ZoneTreeResponse
): { zoneId: number | null; rackId: number | null } {
  if (!initial) return { zoneId: null, rackId: null };
  if (initial.rackLevelId !== null) {
    for (const zone of tree.zones) {
      for (const rack of zone.racks) {
        if (rack.levels.some((l) => l.id === initial.rackLevelId)) {
          return { zoneId: zone.id, rackId: rack.id };
        }
      }
    }
  }
  if (initial.rackId !== null) {
    for (const zone of tree.zones) {
      if (zone.racks.some((r) => r.id === initial.rackId)) {
        return { zoneId: zone.id, rackId: initial.rackId };
      }
    }
  }
  return { zoneId: initial.zoneId, rackId: null };
}

// 장비 등록/수정 공용 폼(이슈 #99, contract §4.10) — 위치는 존→랙→층 단계적 선택,
// 최소 하나 필수는 서버(C001)가 최종 검증한다. metrics는 kind=SENSOR일 때만 노출.
export default function DeviceForm({ tree, initial, submitting, error, submitLabel, onSubmit, onCancel }: DeviceFormProps) {
  const [initialLocation] = useState(() => resolveInitialLocation(initial, tree));

  const [name, setName] = useState(initial?.name ?? "");
  const [kind, setKind] = useState<DeviceKind>(initial?.kind ?? "SENSOR");
  const [model, setModel] = useState(initial?.model ?? "");
  const [serial, setSerial] = useState(initial?.serial ?? "");
  const [status, setStatus] = useState<DeviceStatus>(initial?.status ?? "NORMAL");
  const [calibrationDueAt, setCalibrationDueAt] = useState(
    initial?.calibrationDueAt ? initial.calibrationDueAt.slice(0, 10) : ""
  );
  const [installedOn, setInstalledOn] = useState(initial?.installedOn ?? "");
  const [metrics, setMetrics] = useState<SensorMetric[]>(initial?.metrics ?? []);

  const [zoneId, setZoneId] = useState<number | null>(initialLocation.zoneId);
  const [rackId, setRackId] = useState<number | null>(initialLocation.rackId);
  const [rackLevelId, setRackLevelId] = useState<number | null>(initial?.rackLevelId ?? null);

  const zone = tree.zones.find((z) => z.id === zoneId) ?? null;
  const rack = zone?.racks.find((r) => r.id === rackId) ?? null;

  function toggleMetric(metric: SensorMetric) {
    setMetrics((prev) => (prev.includes(metric) ? prev.filter((m) => m !== metric) : [...prev, metric]));
  }

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    onSubmit({
      name,
      kind,
      model: model || null,
      serial: serial || null,
      status,
      calibrationDueAt: calibrationDueAt ? new Date(calibrationDueAt).toISOString() : null,
      installedOn: installedOn || null,
      zoneId,
      rackId,
      rackLevelId,
      metrics: kind === "SENSOR" ? metrics : [],
    });
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <FormField
        id="device-name"
        label="장비명"
        required
        maxLength={50}
        value={name}
        onChange={(e) => setName(e.target.value)}
      />

      <div className="flex flex-col gap-1.5">
        <label htmlFor="device-kind" className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
          종류
        </label>
        <select
          id="device-kind"
          value={kind}
          onChange={(e) => setKind(e.target.value as DeviceKind)}
          className="rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-900 outline-none focus:border-zinc-500 focus:ring-1 focus:ring-zinc-500 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50"
        >
          {KIND_OPTIONS.map((k) => (
            <option key={k} value={k}>
              {DEVICE_KIND_LABELS[k]}
            </option>
          ))}
        </select>
      </div>

      {kind === "SENSOR" && (
        <div className="flex flex-col gap-1.5">
          <span className="text-sm font-medium text-zinc-700 dark:text-zinc-300">측정 지표 (1개 이상)</span>
          <div className="flex flex-wrap gap-1.5">
            {METRIC_OPTIONS.map((m) => {
              const on = metrics.includes(m);
              return (
                <button
                  key={m}
                  type="button"
                  aria-pressed={on}
                  onClick={() => toggleMetric(m)}
                  className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
                    on
                      ? "border-zinc-900 bg-zinc-900 text-white dark:border-zinc-50 dark:bg-zinc-50 dark:text-zinc-900"
                      : "border-zinc-300 bg-white text-zinc-600 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-400"
                  }`}
                >
                  {SENSOR_METRIC_LABELS[m]}
                </button>
              );
            })}
          </div>
        </div>
      )}

      <div className="grid grid-cols-3 gap-2">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="device-zone" className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
            존
          </label>
          <select
            id="device-zone"
            value={zoneId ?? ""}
            onChange={(e) => {
              const v = e.target.value ? Number(e.target.value) : null;
              setZoneId(v);
              setRackId(null);
              setRackLevelId(null);
            }}
            className="rounded-md border border-zinc-300 bg-white px-2 py-2 text-sm text-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50"
          >
            <option value="">선택 안 함</option>
            {tree.zones.map((z) => (
              <option key={z.id} value={z.id}>
                {z.name}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="device-rack" className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
            랙
          </label>
          <select
            id="device-rack"
            value={rackId ?? ""}
            disabled={!zone}
            onChange={(e) => {
              const v = e.target.value ? Number(e.target.value) : null;
              setRackId(v);
              setRackLevelId(null);
            }}
            className="rounded-md border border-zinc-300 bg-white px-2 py-2 text-sm text-zinc-900 disabled:opacity-50 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50"
          >
            <option value="">선택 안 함</option>
            {zone?.racks.map((r) => (
              <option key={r.id} value={r.id}>
                {r.code}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="device-level" className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
            층
          </label>
          <select
            id="device-level"
            value={rackLevelId ?? ""}
            disabled={!rack}
            onChange={(e) => setRackLevelId(e.target.value ? Number(e.target.value) : null)}
            className="rounded-md border border-zinc-300 bg-white px-2 py-2 text-sm text-zinc-900 disabled:opacity-50 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50"
          >
            <option value="">선택 안 함</option>
            {rack?.levels.map((l) => (
              <option key={l.id} value={l.id}>
                {l.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-2">
        <FormField id="device-model" label="모델 (선택)" maxLength={50} value={model} onChange={(e) => setModel(e.target.value)} />
        <FormField id="device-serial" label="시리얼 (선택)" maxLength={50} value={serial} onChange={(e) => setSerial(e.target.value)} />
      </div>

      <div className="grid grid-cols-2 gap-2">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="device-status" className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
            상태
          </label>
          <select
            id="device-status"
            value={status}
            onChange={(e) => setStatus(e.target.value as DeviceStatus)}
            className="rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50"
          >
            {STATUS_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {DEVICE_STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </div>
        <FormField
          id="device-installed-on"
          label="설치일 (선택)"
          type="date"
          value={installedOn ?? ""}
          onChange={(e) => setInstalledOn(e.target.value)}
        />
      </div>

      <FormField
        id="device-calibration-due"
        label="보정 예정일 (선택)"
        type="date"
        value={calibrationDueAt}
        onChange={(e) => setCalibrationDueAt(e.target.value)}
      />

      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

      <div className="flex gap-2">
        <button
          type="submit"
          disabled={submitting}
          className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
        >
          {submitting ? "저장 중..." : submitLabel}
        </button>
        <button type="button" onClick={onCancel} className="rounded-md border border-zinc-300 px-4 py-2 text-sm dark:border-zinc-700">
          취소
        </button>
      </div>
    </form>
  );
}
