"use client";

import { useEffect, useMemo, useState } from "react";
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Card, CardTitle, Chip } from "@/components/monitoring/ui";
import SimulatedBadge from "@/components/monitoring/SimulatedBadge";
import { ENV_HISTORY_RANGE_LABELS, READING_CELL_STATE_LABELS, READING_METRIC_LIMIT, SENSOR_METRIC_LABELS } from "@/constants";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { getLevelSummary, getReadingSeries } from "@/lib/api/readings";
import { getZoneTree } from "@/lib/api/zones";
import { flattenRacks } from "@/lib/zoneTree";
import { useIsDarkMode } from "@/lib/useIsDarkMode";
import type {
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

const LINE_COLORS = ["#3b82f6", "#f59e0b", "#10b981", "#ef4444"];

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

function buildScopeOptions(tree: ZoneTreeResponse | null): ScopeOption[] {
  if (!tree) return [{ value: "farm", label: "농장 전체" }];
  const options: ScopeOption[] = [{ value: "farm", label: "농장 전체" }];
  for (const zone of tree.zones) {
    options.push({ value: `zone:${zone.id}`, label: `존 · ${zone.name}` });
    for (const rack of zone.racks) {
      options.push({ value: `rack:${rack.id}`, label: `랙 · ${zone.name} ${rack.code}` });
    }
  }
  return options;
}

// 데이터 분석 탭(이슈 #99) — GET /readings/series(시계열) + GET /readings/level-summary(층별
// 비교표)만 쓴다. 목업의 CSV 내보내기·분석 저장·리포트·저장한 분석은 대응 API가 없어 뺐다.
export default function FarmDataAnalysis({ farmId }: FarmDataAnalysisProps) {
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

  const isDark = useIsDarkMode();
  const gridColor = isDark ? "#3f3f46" : "#e4e4e7";
  const axisColor = isDark ? "#a1a1aa" : "#71717a";

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

  return (
    <div className="flex flex-col gap-4 px-6 py-6">
      {/* 페이지 제목(이슈 #136 — 구 FarmTabsHeader 제거로 이 화면엔 제목이 없었다. 시안 페이지
          제목 규격 17px/700을 그대로 적용). */}
      <h1 className="text-[17px] leading-[1.2] font-bold text-dp-ink">데이터 분석</h1>

      {treeError && <p className="text-sm text-red-600 dark:text-red-400">{treeError}</p>}

      <div className="flex flex-wrap items-center gap-2">
        <span className="text-xs font-medium text-zinc-500 dark:text-zinc-400">기간</span>
        {RANGES.map((r) => (
          <Chip key={r} as="button" size="sm" active={r === range} onClick={() => setRange(r)}>
            {ENV_HISTORY_RANGE_LABELS[r]}
          </Chip>
        ))}
        <span className="ml-3 text-xs font-medium text-zinc-500 dark:text-zinc-400">대상</span>
        <select
          value={scope}
          onChange={(e) => setScope(e.target.value)}
          className="rounded-md border border-zinc-300 bg-white px-2 py-1.5 text-xs text-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50"
        >
          {scopeOptions.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      </div>

      <div className="flex flex-wrap items-center gap-1.5">
        <span className="mr-1 text-xs font-medium text-zinc-500 dark:text-zinc-400">항목</span>
        {METRICS.map((m) => (
          <Chip key={m} as="button" size="sm" active={metrics.includes(m)} onClick={() => toggleMetric(m)}>
            {SENSOR_METRIC_LABELS[m]}
          </Chip>
        ))}
        {limitNotice && (
          <span className="text-xs font-medium text-amber-600 dark:text-amber-400">
            항목은 최대 {READING_METRIC_LIMIT}개까지 선택할 수 있습니다
          </span>
        )}
      </div>

      <Card className="flex flex-col gap-3 px-[18px] py-4">
        <div className="flex flex-wrap items-center gap-2.5">
          <CardTitle size="lg">시계열</CardTitle>
          {series && <SimulatedBadge simulated={series.simulated} />}
        </div>

        {metrics.length === 0 && (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">항목을 하나 이상 선택하세요.</p>
        )}
        {metrics.length > 0 && seriesError && <p className="text-sm text-red-600 dark:text-red-400">{seriesError}</p>}
        {metrics.length > 0 && !seriesError && seriesLoading && (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">불러오는 중...</p>
        )}
        {metrics.length > 0 && !seriesError && !seriesLoading && series && series.series.length === 0 && (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">표시할 측정값이 없습니다.</p>
        )}

        {metrics.length > 0 && !seriesError && !seriesLoading && series && series.series.length > 0 && (
          <div className="flex flex-col gap-6">
            {series.series.map((s, i) => (
              <div key={s.metric}>
                <p className="mb-1 text-xs text-zinc-400 dark:text-zinc-500">
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
                        stroke={LINE_COLORS[i % LINE_COLORS.length]}
                        dot={false}
                        connectNulls={false}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>

      {flatRacks.length > 0 && (
        <Card className="flex flex-col gap-3 px-4 py-3.5">
          <div className="flex flex-wrap items-center gap-2.5">
            <CardTitle>층별 평균 비교</CardTitle>
            {levelSummary && <SimulatedBadge simulated={levelSummary.simulated} />}
            <div className="flex-1" />
            <span className="text-xs font-medium text-zinc-500 dark:text-zinc-400">랙</span>
            <select
              value={rackId ?? ""}
              onChange={(e) => setRackId(e.target.value ? Number(e.target.value) : null)}
              className="rounded-md border border-zinc-300 bg-white px-2 py-1.5 text-xs text-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50"
            >
              {flatRacks.map((r) => (
                <option key={r.rackId} value={r.rackId}>
                  {r.zoneName} · {r.rackCode}
                </option>
              ))}
            </select>
          </div>

          {levelSummaryError && <p className="text-sm text-red-600 dark:text-red-400">{levelSummaryError}</p>}
          {!levelSummaryError && levelSummaryLoading && (
            <p className="text-sm text-zinc-500 dark:text-zinc-400">불러오는 중...</p>
          )}
          {!levelSummaryError && !levelSummaryLoading && levelSummary && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[520px] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-zinc-200 text-left text-xs text-zinc-500 dark:border-zinc-800 dark:text-zinc-400">
                    <th className="py-2 pr-3 font-medium">층</th>
                    {levelSummary.levels[0]?.metrics.map((cell) => (
                      <th key={cell.metric} className="py-2 pr-3 font-medium">
                        {SENSOR_METRIC_LABELS[cell.metric]} ({cell.unit})
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {levelSummary.levels.map((row) => (
                    <tr key={row.levelNo} className="border-b border-zinc-100 dark:border-zinc-900">
                      <td className="py-2 pr-3 font-medium text-zinc-900 dark:text-zinc-50">{row.label}</td>
                      {row.metrics.map((cell) => (
                        <td key={cell.metric} className="py-2 pr-3">
                          {cell.average === null ? (
                            <span className="text-zinc-400 dark:text-zinc-600">
                              {READING_CELL_STATE_LABELS[cell.state]}
                            </span>
                          ) : (
                            <span
                              className={
                                cell.state === "CRITICAL"
                                  ? "font-semibold text-red-600 dark:text-red-400"
                                  : cell.state === "WARNING"
                                    ? "font-semibold text-amber-600 dark:text-amber-400"
                                    : "text-zinc-700 dark:text-zinc-300"
                              }
                            >
                              {cell.average.toFixed(1)}
                              {cell.deviationPercent !== null ? ` (${cell.deviationPercent > 0 ? "+" : ""}${cell.deviationPercent.toFixed(0)}%)` : ""}
                            </span>
                          )}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}

      {tree && flatRacks.length === 0 && (
        <p className="text-sm text-zinc-500 dark:text-zinc-400">등록된 랙이 없어 층별 비교표를 표시할 수 없습니다.</p>
      )}
    </div>
  );
}
