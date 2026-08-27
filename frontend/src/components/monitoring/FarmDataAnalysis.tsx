"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Card, CardTitle, Chip } from "@/components/monitoring/ui";
import SimulatedBadge from "@/components/monitoring/SimulatedBadge";
import { ENV_HISTORY_RANGE_LABELS, READING_CELL_STATE_LABELS, READING_METRIC_LIMIT, SENSOR_METRIC_LABELS } from "@/constants";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { getFarm } from "@/lib/api/farms";
import { exportReadingsCsv, getLevelSummary, getReadingSeries } from "@/lib/api/readings";
import { createSavedAnalysis, listSavedAnalyses } from "@/lib/api/savedAnalyses";
import { getZoneTree } from "@/lib/api/zones";
import { hasFarmRoleAtLeast } from "@/lib/roles";
import { flattenRacks } from "@/lib/zoneTree";
import { notifySavedAnalysesChanged } from "@/lib/savedAnalysesBus";
import { useIsDarkMode } from "@/lib/useIsDarkMode";
import type {
  AlarmScopeType,
  FarmResponse,
  LevelSummaryResponse,
  ReadingRange,
  ReadingSeriesResponse,
  SensorMetric,
  ZoneTreeResponse,
} from "@/types";

interface FarmDataAnalysisProps {
  farmId: string;
}

const METRICS: SensorMetric[] = ["TEMPERATURE", "HUMIDITY", "CO2", "EC", "PH", "PPFD", "POWER"];
const RANGES: ReadingRange[] = ["24h", "7d", "30d"];

// 시안 항목 칩 색(#144 03 그래프 분석) — 온도/습도/CO2는 고유색, 그 외는 공용 팔레트 순환.
const METRIC_CHIP_ACTIVE_CLASS: Partial<Record<SensorMetric, string>> = {
  TEMPERATURE: "border border-dp-green-line bg-dp-green-tint-2 text-dp-green-ink",
  HUMIDITY: "border border-dp-blue bg-dp-blue-tint text-dp-blue-ink",
  CO2: "border border-dp-amber-line bg-dp-amber-tint-2 text-dp-amber-sub",
};
const METRIC_LINE_COLOR: Partial<Record<SensorMetric, string>> = {
  TEMPERATURE: "var(--dp-green)",
  HUMIDITY: "var(--dp-blue)",
  CO2: "var(--dp-amber)",
};
const FALLBACK_LINE_COLORS = ["#6366f1", "#ec4899", "#0891b2", "#84cc16"];

function fallbackLineColor(metric: SensorMetric): string {
  const idx = METRICS.filter((m) => !(m in METRIC_LINE_COLOR)).indexOf(metric);
  return FALLBACK_LINE_COLORS[idx % FALLBACK_LINE_COLORS.length];
}

function formatTick(at: string, range: ReadingRange): string {
  const date = new Date(at);
  if (Number.isNaN(date.getTime())) return "";
  return range === "24h"
    ? date.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })
    : date.toLocaleDateString("ko-KR", { month: "2-digit", day: "2-digit" });
}

interface ScopeOption {
  value: string;
  label: string;
}

// scope 문자열(readings API 규약, contract §4.11) ↔ 저장한 분석의 scopeType/scopeId 상호 변환.
function scopeToSaveFields(scope: string): { scopeType: AlarmScopeType; scopeId: number | null } {
  if (scope === "farm") return { scopeType: "FARM", scopeId: null };
  const [kind, idStr] = scope.split(":");
  const id = Number(idStr);
  const scopeType: AlarmScopeType = kind === "zone" ? "ZONE" : kind === "rack" ? "RACK" : "LEVEL";
  return { scopeType, scopeId: Number.isFinite(id) ? id : null };
}

function saveFieldsToScope(scopeType: AlarmScopeType, scopeId: number | null): string {
  if (scopeType === "FARM" || scopeId === null) return "farm";
  return `${scopeType.toLowerCase()}:${scopeId}`;
}

function buildScopeOptions(tree: ZoneTreeResponse | null): ScopeOption[] {
  if (!tree) return [{ value: "farm", label: "농장 전체" }];
  const options: ScopeOption[] = [{ value: "farm", label: "농장 전체" }];
  for (const zone of tree.zones) {
    options.push({ value: `zone:${zone.id}`, label: `존 · ${zone.name}` });
    for (const rack of zone.racks) {
      options.push({ value: `rack:${rack.id}`, label: `랙 · ${zone.name} ${rack.code}` });
      for (const level of rack.levels) {
        // level.label은 랙 생성 시 서버가 채우지 않아 비어 있을 수 있다(RackService#createLevels) —
        // levelNo는 항상 있는 값이라 그걸로 안전하게 대체 표기한다("null" 노출 방지).
        const levelLabel = level.label || `${level.levelNo}층`;
        options.push({ value: `level:${level.id}`, label: `층 · ${zone.name} ${rack.code} ${levelLabel}` });
      }
    }
  }
  return options;
}

// 그래프 분석 화면(이슈 #99 → #144 시안 03 적용) — GET /readings/series(시계열) +
// GET /readings/level-summary(층별 비교표) + CSV 내보내기 · 저장한 분석(#126). 시안의
// "리포트 카드"·"리포트 예약 설정"은 PDF/XLSX가 범위 밖(#126 사용자 결정)이라 렌더하지 않는다.
export default function FarmDataAnalysis({ farmId }: FarmDataAnalysisProps) {
  const router = useRouter();
  const searchParams = useSearchParams();

  const [farm, setFarm] = useState<FarmResponse | null>(null);
  const canSave = hasFarmRoleAtLeast(farm?.myRole, "OPERATOR");

  const [tree, setTree] = useState<ZoneTreeResponse | null>(null);
  const [treeError, setTreeError] = useState<string | null>(null);

  const [metrics, setMetrics] = useState<SensorMetric[]>(["TEMPERATURE"]);
  const [range, setRange] = useState<ReadingRange>("24h");
  const [scope, setScope] = useState("farm");
  const [limitNotice, setLimitNotice] = useState(false);

  const [series, setSeries] = useState<ReadingSeriesResponse | null>(null);
  const [seriesError, setSeriesError] = useState<string | null>(null);
  const [seriesLoading, setSeriesLoading] = useState(true);

  const [rackId, setRackId] = useState<number | null>(null);
  const [levelSummary, setLevelSummary] = useState<LevelSummaryResponse | null>(null);
  const [levelSummaryError, setLevelSummaryError] = useState<string | null>(null);
  const [levelSummaryLoading, setLevelSummaryLoading] = useState(false);

  const [csvExporting, setCsvExporting] = useState(false);
  const [csvError, setCsvError] = useState<string | null>(null);

  const [saveOpen, setSaveOpen] = useState(false);
  const [saveName, setSaveName] = useState("");
  const [saveSubmitting, setSaveSubmitting] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const isDark = useIsDarkMode();
  const gridColor = isDark ? "#3f3f46" : "#e4e4e7";
  const axisColor = isDark ? "#a1a1aa" : "#71717a";

  useEffect(() => {
    let cancelled = false;
    getFarm(farmId)
      .then((res) => {
        if (!cancelled) setFarm(res);
      })
      .catch(() => {
        // "분석 저장" 버튼 노출 여부에만 쓰인다 — 실패해도 role=null로 보수적 숨김(fail-closed).
      });
    return () => {
      cancelled = true;
    };
  }, [farmId]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setTree(null);
      setTreeError(null);
      setScope("farm");
      setRackId(null);
      try {
        const res = await getZoneTree(farmId);
        if (cancelled) return;
        setTree(res);
        const racks = flattenRacks(res);
        if (racks.length > 0) setRackId(racks[0].rackId);
      } catch (err) {
        if (!cancelled) setTreeError(resolveErrorMessage(err));
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [farmId]);

  // 좌측 내비 "저장한 분석" 클릭(?apply=id) 적용 — 목록에서 대상을 찾아 필터를 반영하고
  // 쿼리스트링은 정리한다(뒤로가기 시 재적용되지 않게).
  useEffect(() => {
    const applyId = searchParams.get("apply");
    if (!applyId) return;
    let cancelled = false;
    listSavedAnalyses(farmId)
      .then((list) => {
        if (cancelled) return;
        const target = list.find((a) => String(a.id) === applyId);
        if (target) {
          setMetrics(target.metrics.slice(0, READING_METRIC_LIMIT));
          setRange(target.range as ReadingRange);
          setScope(saveFieldsToScope(target.scopeType, target.scopeId));
        }
      })
      .catch(() => {
        // 적용 실패는 조용히 무시 — 필터는 기존 값 유지.
      })
      .finally(() => {
        if (!cancelled) router.replace(`/farms/${farmId}/data`);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [farmId, searchParams]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      if (metrics.length === 0) {
        setSeries(null);
        setSeriesLoading(false);
        return;
      }
      setSeriesLoading(true);
      setSeriesError(null);
      try {
        const res = await getReadingSeries(farmId, { metrics, range, scope });
        if (!cancelled) {
          setSeries(res);
          setSeriesLoading(false);
        }
      } catch (err) {
        if (!cancelled) {
          setSeriesError(resolveErrorMessage(err));
          setSeriesLoading(false);
        }
      }
    }
    load();
    return () => {
      cancelled = true;
    };
    // metrics 배열 자체는 deps에서 뺐다 — 매 렌더 새 배열 identity라 넣으면 무한 재요청이 된다.
    // load()는 이 렌더의 클로저가 캡처한 metrics를 그대로 쓰므로 join(",")로 "값이 실제로
    // 바뀔 때만" 재실행되게 하는 것으로 충분하고 안전하다(참조가 아니라 내용 비교).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [farmId, range, scope, metrics.join(",")]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      if (rackId === null) {
        setLevelSummary(null);
        return;
      }
      setLevelSummaryLoading(true);
      setLevelSummaryError(null);
      try {
        const res = await getLevelSummary(farmId, { rackId, range });
        if (!cancelled) {
          setLevelSummary(res);
          setLevelSummaryLoading(false);
        }
      } catch (err) {
        if (!cancelled) {
          setLevelSummaryError(resolveErrorMessage(err));
          setLevelSummaryLoading(false);
        }
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [farmId, rackId, range]);

  const scopeOptions = useMemo(() => buildScopeOptions(tree), [tree]);
  const flatRacks = useMemo(() => (tree ? flattenRacks(tree) : []), [tree]);

  function toggleMetric(metric: SensorMetric) {
    setMetrics((prev) => {
      if (prev.includes(metric)) {
        setLimitNotice(false);
        return prev.filter((m) => m !== metric);
      }
      if (prev.length >= READING_METRIC_LIMIT) {
        setLimitNotice(true);
        return prev;
      }
      setLimitNotice(false);
      return [...prev, metric];
    });
  }

  async function handleExportCsv() {
    if (metrics.length === 0 || csvExporting) return;
    setCsvError(null);
    setCsvExporting(true);
    try {
      const { blob, filename } = await exportReadingsCsv(farmId, { metrics, range, scope });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      setCsvError(resolveErrorMessage(err));
    } finally {
      setCsvExporting(false);
    }
  }

  async function handleSaveSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (saveSubmitting) return;
    const trimmed = saveName.trim();
    if (trimmed.length === 0) {
      setSaveError("이름을 입력해주세요.");
      return;
    }
    setSaveError(null);
    setSaveSubmitting(true);
    try {
      const { scopeType, scopeId } = scopeToSaveFields(scope);
      await createSavedAnalysis(farmId, { name: trimmed, metrics, range, scopeType, scopeId });
      setSaveOpen(false);
      setSaveName("");
      notifySavedAnalysesChanged();
    } catch (err) {
      setSaveError(resolveErrorMessage(err));
    } finally {
      setSaveSubmitting(false);
    }
  }

  return (
    <div className="flex h-full flex-col gap-3 px-[30px] py-[26px]">
      <div className="flex items-center gap-2.5">
        <h1 className="text-[17px] leading-[1.2] font-bold text-dp-ink">그래프 분석</h1>
        <div className="flex-1" />
        <div className="flex gap-1.5">
          {RANGES.map((r) => (
            <Chip key={r} as="button" active={r === range} onClick={() => setRange(r)}>
              {ENV_HISTORY_RANGE_LABELS[r]}
            </Chip>
          ))}
        </div>
        <button
          type="button"
          disabled={metrics.length === 0 || csvExporting}
          onClick={handleExportCsv}
          className="rounded-md border border-dp-line-strong bg-dp-surface px-[13px] py-[7px] text-xs font-medium text-dp-body disabled:opacity-40"
        >
          {csvExporting ? "내보내는 중..." : "CSV"}
        </button>
        {canSave && (
          <button
            type="button"
            disabled={metrics.length === 0}
            onClick={() => {
              setSaveError(null);
              setSaveName("");
              setSaveOpen(true);
            }}
            className="rounded-md bg-dp-green px-[13px] py-[7px] text-xs font-semibold text-dp-on-green disabled:opacity-40"
          >
            분석 저장
          </button>
        )}
      </div>

      {treeError && <p className="text-sm text-dp-red-ink">{treeError}</p>}
      {csvError && <p className="text-sm text-dp-red-ink">{csvError}</p>}

      <div className="flex flex-wrap items-center gap-[7px]">
        <span className="mr-[3px] text-[11.5px] font-medium text-dp-muted">항목</span>
        {METRICS.map((m) => {
          const active = metrics.includes(m);
          const activeClass = METRIC_CHIP_ACTIVE_CLASS[m] ?? "bg-dp-ink font-semibold text-dp-surface";
          return (
            <button
              key={m}
              type="button"
              aria-pressed={active}
              onClick={() => toggleMetric(m)}
              className={`rounded-full px-3 py-1.5 text-[11.5px] font-semibold transition-colors ${
                active ? activeClass : "border border-dp-line-strong bg-dp-surface font-medium text-dp-body"
              }`}
            >
              {SENSOR_METRIC_LABELS[m]}
            </button>
          );
        })}
        {limitNotice && (
          <span className="text-xs font-medium text-dp-amber-deep">
            항목은 최대 {READING_METRIC_LIMIT}개까지 선택할 수 있습니다
          </span>
        )}
        <div className="flex-1" />
        <span className="text-[11.5px] font-medium text-dp-muted">대상</span>
        <select
          value={scope}
          onChange={(e) => setScope(e.target.value)}
          className="rounded-md border border-dp-line-strong bg-dp-surface px-3 py-1.5 text-[11.5px] text-dp-body"
        >
          {scopeOptions.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      </div>

      <Card className="flex min-h-0 flex-1 flex-col gap-3 px-[18px] py-4">
        <div className="flex flex-wrap items-center gap-2.5">
          <CardTitle size="lg">시계열</CardTitle>
          {series && <SimulatedBadge simulated={series.simulated} />}
        </div>

        {metrics.length === 0 && <p className="text-sm text-dp-sub">항목을 하나 이상 선택하세요.</p>}
        {metrics.length > 0 && seriesError && <p className="text-sm text-dp-red-ink">{seriesError}</p>}
        {metrics.length > 0 && !seriesError && seriesLoading && <p className="text-sm text-dp-sub">불러오는 중...</p>}
        {metrics.length > 0 && !seriesError && !seriesLoading && series && series.series.length === 0 && (
          <p className="text-sm text-dp-sub">표시할 측정값이 없습니다.</p>
        )}

        {metrics.length > 0 && !seriesError && !seriesLoading && series && series.series.length > 0 && (
          <div className="flex flex-col gap-6">
            {series.series.map((s) => {
              const color = METRIC_LINE_COLOR[s.metric] ?? fallbackLineColor(s.metric);
              return (
                <div key={s.metric}>
                  <p className="mb-1 text-xs text-dp-faint">
                    {SENSOR_METRIC_LABELS[s.metric]} ({s.unit})
                  </p>
                  <div className="h-56 w-full">
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={s.points}>
                        <CartesianGrid stroke={gridColor} strokeDasharray="3 3" />
                        <XAxis dataKey="at" tickFormatter={(v: string) => formatTick(v, range)} stroke={axisColor} fontSize={11} />
                        <YAxis stroke={axisColor} fontSize={11} width={40} />
                        <Tooltip labelFormatter={(v) => formatTick(typeof v === "string" ? v : String(v ?? ""), range)} />
                        <Legend wrapperStyle={{ fontSize: 12 }} />
                        <Line
                          type="monotone"
                          dataKey="value"
                          name={`${SENSOR_METRIC_LABELS[s.metric]}(${s.unit})`}
                          stroke={color}
                          dot={false}
                          connectNulls={false}
                        />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Card>

      {flatRacks.length > 0 && (
        <Card className="flex flex-none flex-col gap-3 px-4 py-3.5">
          <div className="flex flex-wrap items-center gap-2.5">
            <CardTitle>층별 평균 비교 · {ENV_HISTORY_RANGE_LABELS[range]}</CardTitle>
            {levelSummary && <SimulatedBadge simulated={levelSummary.simulated} />}
            <div className="flex-1" />
            <span className="text-xs font-medium text-dp-muted">랙</span>
            <select
              value={rackId ?? ""}
              onChange={(e) => setRackId(e.target.value ? Number(e.target.value) : null)}
              className="rounded-md border border-dp-line-strong bg-dp-surface px-2 py-1.5 text-xs text-dp-body"
            >
              {flatRacks.map((r) => (
                <option key={r.rackId} value={r.rackId}>
                  {r.zoneName} · {r.rackCode}
                </option>
              ))}
            </select>
          </div>

          {levelSummaryError && <p className="text-sm text-dp-red-ink">{levelSummaryError}</p>}
          {!levelSummaryError && levelSummaryLoading && <p className="text-sm text-dp-sub">불러오는 중...</p>}
          {!levelSummaryError && !levelSummaryLoading && levelSummary && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[520px] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-dp-line text-left text-xs text-dp-muted">
                    <th className="py-2 pr-3 font-medium">층</th>
                    {levelSummary.levels[0]?.metrics.map((cell) => (
                      <th key={cell.metric} className="py-2 pr-3 font-medium">
                        {SENSOR_METRIC_LABELS[cell.metric]} ({cell.unit})
                      </th>
                    ))}
                    <th className="py-2 pr-3 text-right font-medium">편차</th>
                  </tr>
                </thead>
                <tbody>
                  {levelSummary.levels.map((row) => (
                    <tr key={row.levelNo} className="border-b border-dp-line-row">
                      {/* row.label은 서버가 랙 생성 시 채우지 않아 비어 있을 수 있다 — levelNo로 폴백. */}
                      <td className="py-2 pr-3 font-medium text-dp-ink">{row.label || `${row.levelNo}층`}</td>
                      {row.metrics.map((cell) => (
                        <td key={cell.metric} className="py-2 pr-3 text-dp-body">
                          {cell.average === null ? (
                            <span className="text-dp-faint">{READING_CELL_STATE_LABELS[cell.state]}</span>
                          ) : (
                            cell.average.toFixed(1)
                          )}
                        </td>
                      ))}
                      <td className="py-2 pr-3 text-right font-semibold">
                        {(() => {
                          // 편차 열은 이 층의 metrics 중 상태가 가장 나쁜 값을 대표로 표시한다
                          // (표 폭 제약 — 시안은 지표별이 아닌 층별 단일 편차 열이다).
                          const worst = row.metrics.reduce<
                            (typeof row.metrics)[number] | null
                          >((acc, cell) => {
                            if (cell.deviationPercent === null) return acc;
                            if (!acc || Math.abs(cell.deviationPercent) > Math.abs(acc.deviationPercent ?? 0)) {
                              return cell;
                            }
                            return acc;
                          }, null);
                          if (!worst || worst.deviationPercent === null) return <span className="text-dp-faint">-</span>;
                          const tone =
                            worst.state === "CRITICAL"
                              ? "text-dp-red-ink"
                              : worst.state === "WARNING"
                                ? "text-dp-amber-ink"
                                : "text-dp-green";
                          return (
                            <span className={tone}>
                              {worst.deviationPercent > 0 ? "+" : ""}
                              {worst.deviationPercent.toFixed(1)}
                            </span>
                          );
                        })()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}

      {tree && flatRacks.length === 0 && (
        <p className="text-sm text-dp-sub">등록된 랙이 없어 층별 비교표를 표시할 수 없습니다.</p>
      )}

      {saveOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4" onClick={() => setSaveOpen(false)}>
          <form
            onSubmit={handleSaveSubmit}
            onClick={(e) => e.stopPropagation()}
            className="flex w-full max-w-sm flex-col gap-3 rounded-lg border border-dp-line bg-dp-surface p-4 shadow-lg"
          >
            <h2 className="text-sm font-semibold text-dp-ink">분석 저장</h2>
            <p className="text-xs text-dp-muted">
              현재 항목({metrics.map((m) => SENSOR_METRIC_LABELS[m]).join(", ")}) · {ENV_HISTORY_RANGE_LABELS[range]} ·{" "}
              {scopeOptions.find((o) => o.value === scope)?.label ?? scope} 조합을 저장합니다.
            </p>
            <input
              autoFocus
              value={saveName}
              onChange={(e) => setSaveName(e.target.value)}
              maxLength={50}
              placeholder="분석 이름"
              className="rounded-md border border-dp-line-strong bg-dp-surface px-3 py-2 text-sm text-dp-ink"
            />
            {saveError && <p className="text-sm text-dp-red-ink">{saveError}</p>}
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setSaveOpen(false)}
                className="rounded-md border border-dp-line-strong px-3 py-1.5 text-sm text-dp-body"
              >
                취소
              </button>
              <button
                type="submit"
                disabled={saveSubmitting}
                className="rounded-md bg-dp-green px-3 py-1.5 text-sm font-semibold text-dp-on-green disabled:opacity-60"
              >
                {saveSubmitting ? "저장 중..." : "저장"}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
