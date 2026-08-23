"use client";

import { useEffect, useState } from "react";
import DeviceForm from "@/components/monitoring/DeviceForm";
import Modal from "@/components/ui/Modal";
import { DEVICE_KIND_LABELS, DEVICE_STATUS_LABELS } from "@/constants";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { getFarm } from "@/lib/api/farms";
import { createDevice, deleteDevice, getDeviceSummary, listDevices, updateDevice } from "@/lib/api/devices";
import { getZoneTree } from "@/lib/api/zones";
import { buildLocationMaps, describeDeviceLocation } from "@/lib/zoneTree";
import type {
  DeviceKind,
  DeviceRequest,
  DeviceResponse,
  DeviceStatus,
  DeviceSummaryResponse,
  FarmResponse,
  ZoneTreeResponse,
} from "@/types";

interface FarmDeviceManagementProps {
  farmId: string;
}

type KindFilter = "ALL" | DeviceKind;
type StatusFilter = "ALL" | DeviceStatus;

const KIND_FILTERS: KindFilter[] = ["ALL", "SENSOR", "CONTROLLER", "GATEWAY"];
const STATUS_FILTERS: StatusFilter[] = ["ALL", "NORMAL", "WARNING", "FAULT", "OFFLINE"];

// 장비·센서 관리 탭(이슈 #99, contract §4.10) — 요약 KPI + 필터 목록 + CRUD.
// 목업의 사용자·권한(멤버 탭 이미 있음)·시스템 로그(대응 API 없음)는 뺐다(handoff 요건 2).
export default function FarmDeviceManagement({ farmId }: FarmDeviceManagementProps) {
  const [farm, setFarm] = useState<FarmResponse | null>(null);
  const [tree, setTree] = useState<ZoneTreeResponse | null>(null);
  const [summary, setSummary] = useState<DeviceSummaryResponse | null>(null);
  const [devices, setDevices] = useState<DeviceResponse[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [kindFilter, setKindFilter] = useState<KindFilter>("ALL");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [zoneFilter, setZoneFilter] = useState<number | "ALL">("ALL");
  const [query, setQuery] = useState("");

  const [refreshKey, setRefreshKey] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<DeviceResponse | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [rowError, setRowError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const isOwner = farm?.myRole === "OWNER";

  useEffect(() => {
    getFarm(farmId)
      .then(setFarm)
      .catch(() => {
        // 헤더(FarmTabsHeader)가 이미 농장 로드 실패를 처리하므로 여기서는 조용히 넘어간다.
      });
  }, [farmId]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const res = await getZoneTree(farmId);
        if (!cancelled) setTree(res);
      } catch {
        // 위치 표시·픽커는 보조 기능이라 실패해도 목록 자체는 계속 보여준다.
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [farmId, refreshKey]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const res = await getDeviceSummary(farmId);
        if (!cancelled) setSummary(res);
      } catch {
        // KPI 카드는 보조 요약이라 실패해도 목록은 계속 보여준다.
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [farmId, refreshKey]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoadError(null);
      try {
        const res = await listDevices(farmId, {
          kind: kindFilter === "ALL" ? undefined : kindFilter,
          status: statusFilter === "ALL" ? undefined : statusFilter,
          zoneId: zoneFilter === "ALL" ? undefined : zoneFilter,
          q: query.trim() || undefined,
        });
        if (!cancelled) setDevices(res.devices);
      } catch (err) {
        if (!cancelled) setLoadError(resolveErrorMessage(err));
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [farmId, kindFilter, statusFilter, zoneFilter, query, refreshKey]);

  const locationMaps = tree ? buildLocationMaps(tree) : null;

  async function handleCreate(payload: DeviceRequest) {
    setFormError(null);
    setSubmitting(true);
    try {
      await createDevice(farmId, payload);
      setCreateOpen(false);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setFormError(resolveErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleEdit(payload: DeviceRequest) {
    if (!editTarget) return;
    setFormError(null);
    setSubmitting(true);
    try {
      await updateDevice(farmId, editTarget.id, payload);
      setEditTarget(null);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setFormError(resolveErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(device: DeviceResponse) {
    if (!window.confirm(`"${device.name}" 장비를 삭제하시겠습니까?`)) return;
    setRowError(null);
    setBusyId(device.id);
    try {
      await deleteDevice(farmId, device.id);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setRowError(resolveErrorMessage(err));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="flex flex-col gap-4 px-6 py-6">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">장비 · 센서</h2>
        {isOwner && tree && (
          <button
            type="button"
            onClick={() => {
              setFormError(null);
              setCreateOpen(true);
            }}
            className="rounded-md bg-zinc-900 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-zinc-700 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            장비 추가
          </button>
        )}
      </div>

      {summary && (
        <div className="grid grid-cols-2 gap-3 min-[900px]:grid-cols-5">
          <KpiCard label="전체" value={summary.total} />
          <KpiCard label="정상" value={summary.normal} tone="ok" />
          <KpiCard label="주의" value={summary.warning} tone="warning" />
          <KpiCard label="고장/통신두절" value={summary.faultOrOffline} tone="critical" />
          <KpiCard label="보정 임박(30일)" value={summary.calibrationDueSoon} />
        </div>
      )}

      <div className="flex flex-wrap items-center gap-2">
        {KIND_FILTERS.map((k) => (
          <button
            key={k}
            type="button"
            aria-pressed={kindFilter === k}
            onClick={() => setKindFilter(k)}
            className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
              kindFilter === k
                ? "border-zinc-900 bg-zinc-900 text-white dark:border-zinc-50 dark:bg-zinc-50 dark:text-zinc-900"
                : "border-zinc-300 bg-white text-zinc-600 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-400"
            }`}
          >
            {k === "ALL" ? "전체 종류" : DEVICE_KIND_LABELS[k]}
          </button>
        ))}
        <span className="mx-1 h-4 w-px bg-zinc-200 dark:bg-zinc-800" />
        {STATUS_FILTERS.map((s) => (
          <button
            key={s}
            type="button"
            aria-pressed={statusFilter === s}
            onClick={() => setStatusFilter(s)}
            className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
              statusFilter === s
                ? "border-zinc-900 bg-zinc-900 text-white dark:border-zinc-50 dark:bg-zinc-50 dark:text-zinc-900"
                : "border-zinc-300 bg-white text-zinc-600 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-400"
            }`}
          >
            {s === "ALL" ? "전체 상태" : DEVICE_STATUS_LABELS[s]}
          </button>
        ))}
        {tree && (
          <select
            value={zoneFilter === "ALL" ? "" : zoneFilter}
            onChange={(e) => setZoneFilter(e.target.value ? Number(e.target.value) : "ALL")}
            className="rounded-md border border-zinc-300 bg-white px-2 py-1.5 text-xs text-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50"
          >
            <option value="">전체 존</option>
            {tree.zones.map((z) => (
              <option key={z.id} value={z.id}>
                {z.name}
              </option>
            ))}
          </select>
        )}
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="장비명 검색"
          aria-label="장비명 검색"
          className="rounded-md border border-zinc-300 bg-white px-2 py-1.5 text-xs text-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50"
        />
      </div>

      {loadError && <p className="text-sm text-red-600 dark:text-red-400">{loadError}</p>}
      {rowError && <p className="text-sm text-red-600 dark:text-red-400">{rowError}</p>}

      {!devices && !loadError && <p className="text-sm text-zinc-500 dark:text-zinc-400">불러오는 중...</p>}

      {devices && devices.length === 0 && (
        <p className="text-sm text-zinc-500 dark:text-zinc-400">조건에 맞는 장비가 없습니다.</p>
      )}

      {devices && devices.length > 0 && (
        <div className="overflow-x-auto rounded-md border border-zinc-200 dark:border-zinc-800">
          <table className="w-full min-w-[720px] border-collapse text-sm">
            <thead>
              <tr className="border-b border-zinc-200 bg-zinc-50 text-left text-xs text-zinc-500 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-400">
                <th className="px-3 py-2 font-medium">장비</th>
                <th className="px-3 py-2 font-medium">위치</th>
                <th className="px-3 py-2 font-medium">모델/시리얼</th>
                <th className="px-3 py-2 font-medium">최종 수신</th>
                <th className="px-3 py-2 font-medium">보정 예정</th>
                <th className="px-3 py-2 font-medium">상태</th>
                {isOwner && <th className="px-3 py-2 font-medium">관리</th>}
              </tr>
            </thead>
            <tbody>
              {devices.map((device) => (
                <tr key={device.id} className="border-b border-zinc-100 last:border-0 dark:border-zinc-900">
                  <td className="px-3 py-2">
                    <div className="font-medium text-zinc-900 dark:text-zinc-50">{device.name}</div>
                    <div className="text-xs text-zinc-400 dark:text-zinc-500">
                      {DEVICE_KIND_LABELS[device.kind]}
                      {device.metrics.length > 0 ? ` · ${device.metrics.join(", ")}` : ""}
                    </div>
                  </td>
                  <td className="px-3 py-2 text-zinc-600 dark:text-zinc-300">
                    {locationMaps ? describeDeviceLocation(device, locationMaps) : "-"}
                  </td>
                  <td className="px-3 py-2 text-zinc-600 dark:text-zinc-300">
                    {device.model || "-"} / {device.serial || "-"}
                  </td>
                  <td className="px-3 py-2 font-mono text-xs text-zinc-500 dark:text-zinc-400">
                    {device.lastSeenAt ? new Date(device.lastSeenAt).toLocaleString("ko-KR") : "-"}
                  </td>
                  <td className="px-3 py-2 text-xs text-zinc-500 dark:text-zinc-400">
                    {device.calibrationDueAt ? new Date(device.calibrationDueAt).toLocaleDateString("ko-KR") : "-"}
                  </td>
                  <td className="px-3 py-2">
                    <StatusChip status={device.status} />
                  </td>
                  {isOwner && (
                    <td className="px-3 py-2">
                      <div className="flex gap-2 text-xs">
                        <button
                          type="button"
                          onClick={() => {
                            setFormError(null);
                            setEditTarget(device);
                          }}
                          className="text-zinc-500 hover:underline dark:text-zinc-400"
                        >
                          수정
                        </button>
                        <button
                          type="button"
                          disabled={busyId === device.id}
                          onClick={() => handleDelete(device)}
                          className="text-red-600 hover:underline disabled:opacity-60 dark:text-red-400"
                        >
                          삭제
                        </button>
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {tree && (
        <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="장비 추가">
          <DeviceForm
            tree={tree}
            submitting={submitting}
            error={formError}
            submitLabel="추가"
            onSubmit={handleCreate}
            onCancel={() => setCreateOpen(false)}
          />
        </Modal>
      )}

      {tree && (
        <Modal open={editTarget !== null} onClose={() => setEditTarget(null)} title="장비 수정">
          {editTarget && (
            <DeviceForm
              tree={tree}
              initial={editTarget}
              submitting={submitting}
              error={formError}
              submitLabel="저장"
              onSubmit={handleEdit}
              onCancel={() => setEditTarget(null)}
            />
          )}
        </Modal>
      )}
    </div>
  );
}

function KpiCard({ label, value, tone }: { label: string; value: number; tone?: "ok" | "warning" | "critical" }) {
  const toneClass =
    tone === "ok"
      ? "text-emerald-600 dark:text-emerald-400"
      : tone === "warning"
        ? "text-amber-600 dark:text-amber-400"
        : tone === "critical"
          ? "text-red-600 dark:text-red-400"
          : "text-zinc-900 dark:text-zinc-50";
  return (
    <div className="rounded-md border border-zinc-200 px-3 py-3 dark:border-zinc-800">
      <div className="text-xs font-medium text-zinc-500 dark:text-zinc-400">{label}</div>
      <div className={`mt-1.5 text-2xl font-bold ${toneClass}`}>{value}</div>
    </div>
  );
}

function StatusChip({ status }: { status: DeviceStatus }) {
  const style =
    status === "NORMAL"
      ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-400"
      : status === "WARNING"
        ? "bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-400"
        : "bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-400";
  return <span className={`rounded px-2 py-0.5 text-xs font-semibold ${style}`}>{DEVICE_STATUS_LABELS[status]}</span>;
}
