"use client";

import { useEffect, useState } from "react";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { ENV_HISTORY_RANGE_LABELS } from "@/constants";
import { getEnvironmentHistory } from "@/lib/api/environment";
import { useIsDarkMode } from "@/lib/useIsDarkMode";
import type { EnvironmentHistoryPoint, EnvironmentHistoryRange } from "@/types";

interface EnvironmentHistoryChartProps {
  farmId: number | string;
}

const RANGES: EnvironmentHistoryRange[] = ["24h", "7d", "30d"];

function formatTick(capturedAt: string, range: EnvironmentHistoryRange): string {
  const date = new Date(capturedAt);
  if (Number.isNaN(date.getTime())) return "";
  return range === "24h"
    ? date.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })
    : date.toLocaleDateString("ko-KR", { month: "2-digit", day: "2-digit" });
}

function formatTooltipLabel(capturedAt: string): string {
  const date = new Date(capturedAt);
  return Number.isNaN(date.getTime()) ? capturedAt : date.toLocaleString("ko-KR");
}

// 환경 시계열 차트(이슈 #53, 다함 벤치마킹 1) — 대시보드 환경 섹션(EnvironmentWidget) 하단 확장.
// 기간 탭(24h/7d/30d) 전환마다 재조회. 다운샘플은 서버가 수행(contract §4.6) —
// FE는 받은 point만 그대로 그리고 null 구간은 선을 끊는다(connectNulls=false).
export default function EnvironmentHistoryChart({ farmId }: EnvironmentHistoryChartProps) {
  const [range, setRange] = useState<EnvironmentHistoryRange>("24h");
  const [points, setPoints] = useState<EnvironmentHistoryPoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [unavailable, setUnavailable] = useState(false);
  const isDark = useIsDarkMode();

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setUnavailable(false);
      try {
        const res = await getEnvironmentHistory(farmId, range);
        if (!cancelled) setPoints(res.points);
      } catch {
        if (!cancelled) setUnavailable(true);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [farmId, range]);

  const gridColor = isDark ? "#3f3f46" : "#e4e4e7";
  const axisColor = isDark ? "#a1a1aa" : "#71717a";
  const tooltipStyle = {
    backgroundColor: isDark ? "#18181b" : "#ffffff",
    borderColor: isDark ? "#3f3f46" : "#e4e4e7",
    fontSize: 12,
  };

  return (
    <section className="flex flex-col gap-4 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">환경 추이</h3>
        <div
          role="tablist"
          aria-label="조회 기간"
          className="flex gap-1 rounded-md border border-zinc-200 p-0.5 dark:border-zinc-800"
        >
          {RANGES.map((r) => (
            <button
              key={r}
              type="button"
              role="tab"
              aria-selected={range === r}
              onClick={() => setRange(r)}
              className={`rounded px-2.5 py-1 text-xs font-medium transition-colors ${
                range === r
                  ? "bg-zinc-900 text-white dark:bg-zinc-50 dark:text-zinc-900"
                  : "text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50"
              }`}
            >
              {ENV_HISTORY_RANGE_LABELS[r]}
            </button>
          ))}
        </div>
      </div>

      {loading && <p className="text-sm text-zinc-500 dark:text-zinc-400">차트 데이터 불러오는 중...</p>}

      {!loading && unavailable && (
        <p className="text-sm text-zinc-500 dark:text-zinc-400">환경 추이를 불러올 수 없습니다.</p>
      )}

      {!loading && !unavailable && points.length === 0 && (
        <p className="text-sm text-zinc-500 dark:text-zinc-400">표시할 데이터가 없습니다.</p>
      )}

      {!loading && !unavailable && points.length > 0 && (
        <div className="flex flex-col gap-6">
          <div>
            <p className="mb-1 text-xs text-zinc-400 dark:text-zinc-500">온도(℃)</p>
            <div className="h-56 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={points}>
                  <CartesianGrid stroke={gridColor} strokeDasharray="3 3" />
                  <XAxis
                    dataKey="capturedAt"
                    tickFormatter={(v: string) => formatTick(v, range)}
                    stroke={axisColor}
                    fontSize={11}
                  />
                  <YAxis stroke={axisColor} fontSize={11} width={36} />
                  <Tooltip
                    labelFormatter={(v) => formatTooltipLabel(typeof v === "string" ? v : String(v ?? ""))}
                    contentStyle={tooltipStyle}
                  />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Line
                    type="monotone"
                    dataKey="outdoorTemp"
                    name="외기"
                    stroke="#3b82f6"
                    dot={false}
                    connectNulls={false}
                  />
                  <Line
                    type="monotone"
                    dataKey="indoorTemp"
                    name="내부"
                    stroke="#f59e0b"
                    dot={false}
                    connectNulls={false}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>

          <div>
            <p className="mb-1 text-xs text-zinc-400 dark:text-zinc-500">습도(%)</p>
            <div className="h-56 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={points}>
                  <CartesianGrid stroke={gridColor} strokeDasharray="3 3" />
                  <XAxis
                    dataKey="capturedAt"
                    tickFormatter={(v: string) => formatTick(v, range)}
                    stroke={axisColor}
                    fontSize={11}
                  />
                  <YAxis stroke={axisColor} fontSize={11} width={36} domain={[0, 100]} />
                  <Tooltip
                    labelFormatter={(v) => formatTooltipLabel(typeof v === "string" ? v : String(v ?? ""))}
                    contentStyle={tooltipStyle}
                  />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Line
                    type="monotone"
                    dataKey="outdoorHumidity"
                    name="외기"
                    stroke="#3b82f6"
                    dot={false}
                    connectNulls={false}
                  />
                  <Line
                    type="monotone"
                    dataKey="indoorHumidity"
                    name="내부"
                    stroke="#f59e0b"
                    dot={false}
                    connectNulls={false}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
