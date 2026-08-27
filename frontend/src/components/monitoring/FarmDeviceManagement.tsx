"use client";

import { useEffect, useState } from "react";
import FarmMembersPreview from "@/components/farms/FarmMembersPreview";
import DeviceForm from "@/components/monitoring/DeviceForm";
import SystemLogPanel from "@/components/monitoring/SystemLogPanel";
import ZoneRackManager from "@/components/monitoring/ZoneRackManager";
import Modal from "@/components/ui/Modal";
import { DEVICE_KIND_LABELS, DEVICE_STATUS_LABELS } from "@/constants";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { getFarm } from "@/lib/api/farms";
import { createDevice, deleteDevice, getDeviceSummary, listDevices, updateDevice } from "@/lib/api/devices";
import { getZoneTree } from "@/lib/api/zones";
import { hasFarmRoleAtLeast } from "@/lib/roles";
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
const STATUS_FILTERS: StatusFilter[] = ["ALL", "NORMAL", "WARNING", "FAULT", "OFFLINE", "OFF"];

// 장비·센서 관리 탭(이슈 #99 → #144 시안 06 적용, contract §4.10) — 요약 KPI + 필터 목록 + CRUD +
// 우측 사용자·권한/시스템 로그 미리보기(#122·#129). 목업의 "보정 일정"은 대응 기능이 없어(handoff
// 요건) 만들지 않았다 — "장비 추가"는 기존 기능 그대로 재사용한다.
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

  // 장비/존/랙 구조 관리는 ADMIN 전용(contract §2, 이슈 #123).
  const isAdmin = hasFarmRoleAtLeast(farm?.myRole, "ADMIN");

  useEffect(() => {
    getFarm(farmId)
      .then(setFarm)
      .catch(() => {
        // ADMIN 전용 UI(isAdmin) 노출 여부에만 쓰인다 — role이 없으면 lib/roles.ts가 항상
        // false로 판정(fail-closed)하므로 실패해도 화면 자체는 계속 보여준다(이슈 #136 —
        // 구 FarmTabsHeader 제거로 더 이상 별도 헤더가 이 실패를 대신 알리지 않는다).
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
    <div className="flex flex-col gap-3.5 px-[30px] py-[26px]">
      <div className="flex items-center gap-2.5">
        <h1 className="text-[17px] leading-[1.2] font-bold text-dp-ink">장비 · 센서 관리</h1>
        <div className="flex-1" />
        {isAdmin && tree && (
          <button
            type="button"
            onClick={() => {
              setFormError(null);
              setCreateOpen(true);
            }}
            className="rounded-md bg-dp-green px-[13px] py-[7px] text-xs font-semibold text-dp-on-green"
          >
            장비 추가
          </button>
        )}
      </div>

      {/* 요약 카드 6장(handoff — devices/summary, contract §4.10). 리뷰 반영(이슈 #144 P1) —
          시안은 4장(등록/정상/통신이상/보정임박)이었으나 warning·off를 카드에서 빼면
          "비상 정지 직후 total=60, normal=0, faultOrOffline=0"이 되어 화면이 "이상 없음"으로
          오독된다(계약 §4.10 경고). 필터 탭으로 대체 가능하다고 판단했었지만 사용자가 능동적으로
          눌러야만 보이는 정보는 "한눈에 보이는 요약"이 아니다 — 원래(사이클 3) 6카드 구성으로
          되돌려 total = normal + warning + faultOrOffline + off 가 화면에서 항상 검산된다. */}
      {summary && (
        <div className="grid grid-cols-2 gap-3 min-[720px]:grid-cols-3 min-[1100px]:grid-cols-6">
          <KpiCard label="등록 장비" value={summary.total} />
          <KpiCard label="정상 통신" value={summary.normal} tone="ok" />
          <KpiCard label="주의" value={summary.warning} tone="warning" />
          <KpiCard label="통신 이상" value={summary.faultOrOffline} tone="critical" />
          <KpiCard label="정지" value={summary.off} />
          <KpiCard label="보정 기한 임박" value={summary.calibrationDueSoon} tone="warning" />
        </div>
      )}

      {tree && (
        <ZoneRackManager farmId={farmId} tree={tree} canManageStructure={isAdmin} onChanged={() => setRefreshKey((k) => k + 1)} />
      )}

      <div className="grid grid-cols-1 gap-3.5 min-[1280px]:grid-cols-[1fr_440px]">
        <div className="flex flex-col gap-3.5 overflow-hidden rounded-[10px] border border-dp-line bg-dp-surface">
          <div className="flex flex-wrap items-center gap-2 border-b border-dp-line p-3">
            {KIND_FILTERS.map((k) => (
              <button
                key={k}
                type="button"
                aria-pressed={kindFilter === k}
                onClick={() => setKindFilter(k)}
                className={`rounded-md px-[11px] py-1.5 text-[11.5px] font-medium transition-colors ${
                  kindFilter === k ? "bg-dp-ink font-semibold text-dp-surface" : "border border-dp-line-strong text-dp-body"
                }`}
              >
                {k === "ALL" ? "전체" : DEVICE_KIND_LABELS[k]}
              </button>
            ))}
            <span className="mx-1 h-4 w-px bg-dp-line-strong" />
            {STATUS_FILTERS.map((s) => (
              <button
                key={s}
                type="button"
                aria-pressed={statusFilter === s}
                onClick={() => setStatusFilter(s)}
                className={`rounded-md px-[11px] py-1.5 text-[11.5px] font-medium transition-colors ${
                  statusFilter === s ? "bg-dp-ink font-semibold text-dp-surface" : "border border-dp-line-strong text-dp-body"
                }`}
              >
                {s === "ALL" ? "전체 상태" : DEVICE_STATUS_LABELS[s]}
              </button>
            ))}
            <div className="flex-1" />
            {tree && (
              <select
                value={zoneFilter === "ALL" ? "" : zoneFilter}
                onChange={(e) => setZoneFilter(e.target.value ? Number(e.target.value) : "ALL")}
                className="rounded-md border border-dp-line-strong bg-dp-surface px-2 py-1.5 text-xs text-dp-body"
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
              placeholder="장비명 · 시리얼 검색"
              aria-label="장비명 · 시리얼 검색"
              className="rounded-md bg-dp-inset px-3 py-1.5 text-xs text-dp-ink placeholder:text-dp-faint"
            />
          </div>

          {loadError && <p className="px-4 text-sm text-dp-red-ink">{loadError}</p>}
          {rowError && <p className="px-4 text-sm text-dp-red-ink">{rowError}</p>}

          {!devices && !loadError && <p className="px-4 pb-4 text-sm text-dp-sub">불러오는 중...</p>}

          {devices && devices.length === 0 && <p className="px-4 pb-4 text-sm text-dp-sub">조건에 맞는 장비가 없습니다.</p>}

          {devices && devices.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-dp-line bg-dp-inset-alt text-left text-xs text-dp-muted">
                    <th className="px-4 py-2 font-medium">장비</th>
                    <th className="px-3 py-2 font-medium">위치</th>
                    <th className="px-3 py-2 font-medium">모델/시리얼</th>
                    <th className="px-3 py-2 font-medium">최종 수신</th>
                    <th className="px-3 py-2 font-medium">보정 예정</th>
                    <th className="px-3 py-2 font-medium">상태</th>
                    {isAdmin && <th className="px-3 py-2 font-medium">관리</th>}
                  </tr>
                </thead>
                <tbody>
                  {devices.map((device) => (
                    <tr
                      key={device.id}
                      className={`border-b border-dp-line-row last:border-0 ${
                        device.status === "FAULT" || device.status === "OFFLINE" ? "bg-dp-red-tint" : ""
                      }`}
                    >
                      <td className="px-4 py-2.5">
                        <div className="font-semibold text-dp-ink">{device.name}</div>
                        <div className="text-xs text-dp-faint">
                          {DEVICE_KIND_LABELS[device.kind]}
                          {device.metrics.length > 0 ? ` · ${device.metrics.join(", ")}` : ""}
                        </div>
                      </td>
                      <td className="px-3 py-2.5 text-dp-body">
                        {locationMaps ? describeDeviceLocation(device, locationMaps) : "-"}
                      </td>
                      <td className="px-3 py-2.5 text-dp-body">
                        {device.model || "-"} / {device.serial || "-"}
                      </td>
                      <td className="px-3 py-2.5 font-mono text-xs text-dp-muted">
                        {device.lastSeenAt ? new Date(device.lastSeenAt).toLocaleString("ko-KR") : "-"}
                      </td>
                      <td
                        className={`px-3 py-2.5 text-xs ${
                          device.status === "WARNING" ? "font-semibold text-dp-amber-ink" : "text-dp-muted"
                        }`}
                      >
                        {device.calibrationDueAt ? new Date(device.calibrationDueAt).toLocaleDateString("ko-KR") : "-"}
                      </td>
                      <td className="px-3 py-2.5">
                        <StatusChip status={device.status} />
                      </td>
                      {isAdmin && (
                        <td className="px-3 py-2.5">
                          <div className="flex gap-2 text-xs">
                            <button
                              type="button"
                              onClick={() => {
                                setFormError(null);
                                setEditTarget(device);
                              }}
                              className="text-dp-muted hover:underline"
                            >
                              수정
                            </button>
                            <button
                              type="button"
                              disabled={busyId === device.id}
                              onClick={() => handleDelete(device)}
                              className="text-dp-red-ink hover:underline disabled:opacity-60"
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
              <div className="border-t border-dp-line px-4 py-2.5 text-xs text-dp-muted">{devices.length}대</div>
            </div>
          )}
        </div>

        <div className="flex flex-col gap-3">
          <FarmMembersPreview farmId={farmId} isAdmin={isAdmin} />
          <SystemLogPanel farmId={farmId} />
        </div>
      </div>

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
      ? "text-dp-green"
      : tone === "warning"
        ? "text-dp-amber-ink"
        : tone === "critical"
          ? "text-dp-red-ink"
          : "text-dp-ink";
  return (
    <div className="rounded-[10px] border border-dp-line bg-dp-surface px-4 py-3.5">
      <div className="text-[11.5px] font-medium text-dp-muted">{label}</div>
      <div className={`mt-[9px] text-2xl font-bold ${toneClass}`}>{value}</div>
    </div>
  );
}

function StatusChip({ status }: { status: DeviceStatus }) {
  const style =
    status === "NORMAL"
      ? "text-dp-green"
      : status === "WARNING"
        ? "text-dp-amber-ink"
        : status === "OFF"
          ? // OFF는 장애가 아니라 제어 조작 결과다(contract §4.12) — FAULT/OFFLINE과 같은 빨강을 쓰면
            // "고장"으로 오인된다.
            "text-dp-muted"
          : "text-dp-red-ink";
  return <span className={`text-[11px] font-semibold ${style}`}>{DEVICE_STATUS_LABELS[status]}</span>;
}
