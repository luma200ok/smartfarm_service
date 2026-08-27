"use client";

import { useEffect, useState } from "react";
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Card, CardTitle } from "@/components/monitoring/ui";
import SimulatedBadge from "@/components/monitoring/SimulatedBadge";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { getReadingSeries } from "@/lib/api/readings";
import { useIsDarkMode } from "@/lib/useIsDarkMode";
import type { ReadingSeriesResponse } from "@/types";

interface FarmTrendChartProps {
  farmId: number;
  farmName?: string;
}

function formatTick(at: string): string {
  const date = new Date(at);
  return Number.isNaN(date.getTime()) ? "" : date.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
}

// 홈 대시보드 농장 24시간 추이(이슈 #99, #142) — GET /readings/series(scope=farm, 온도·습도)만 쓴다.
// scope=farm은 층 간 평균 1계열로 축약된다(계약 §4.11 응답 크기 상한).
//
// ⚠️ 시안(#142)의 "농장 간 온도·습도" 다중 계열 그래프는 만들지 않는다 — readings/series는
// 농장 단건 스코프라 여러 농장을 겹쳐 그리려면 농장 수만큼 반복 호출해야 하고, 그건 #139가
// 없애려던 N+1을 이 패널에서 재생산하는 셈이다. 대신 상단에서 선택한 농장 1건의 추이로
// 대체한다(handoff 요건 — "범위에서 빼거나 선택 농장 단일 추이로 대체").
export default function FarmTrendChart({ farmId, farmName }: FarmTrendChartProps) {
  const [data, setData] = useState<ReadingSeriesResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const isDark = useIsDarkMode();

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setData(null);
      setError(null);
      try {
        const res = await getReadingSeries(farmId, {
          metrics: ["TEMPERATURE", "HUMIDITY"],
          range: "24h",
          scope: "farm",
        });
        if (!cancelled) setData(res);
      } catch (err) {
        if (!cancelled) setError(resolveErrorMessage(err));
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [farmId]);

  const gridColor = isDark ? "#3f3f46" : "#e4e4e7";
  const axisColor = isDark ? "#a1a1aa" : "#71717a";

  // 시각(at) 기준으로 온도·습도 계열을 한 배열로 합친다(recharts는 dataKey 단위 join 없음).
  const merged: { at: string; TEMPERATURE?: number | null; HUMIDITY?: number | null }[] = [];
  if (data) {
    const byAt = new Map<string, { at: string; TEMPERATURE?: number | null; HUMIDITY?: number | null }>();
    for (const series of data.series) {
      for (const point of series.points) {
        const row = byAt.get(point.at) ?? { at: point.at };
        row[series.metric as "TEMPERATURE" | "HUMIDITY"] = point.value;
        byAt.set(point.at, row);
      }
    }
    merged.push(...Array.from(byAt.values()).sort((a, b) => a.at.localeCompare(b.at)));
  }

  return (
    <Card className="flex flex-col gap-3 px-[18px] py-4">
      <div className="flex flex-wrap items-baseline gap-2.5">
        <CardTitle size="lg">{farmName ? `${farmName} · 온도 · 습도` : "농장 온도 · 습도"} (24시간)</CardTitle>
        {data && <SimulatedBadge simulated={data.simulated} />}
      </div>

      {error && <p className="text-[12px] leading-[1.5] text-dp-red-ink">{error}</p>}
      {!error && !data && <p className="text-[12px] leading-[1.5] text-dp-muted">불러오는 중...</p>}
      {!error && data && merged.length === 0 && (
        <p className="text-[12px] leading-[1.5] text-dp-muted">표시할 측정값이 없습니다.</p>
      )}

      {!error && data && merged.length > 0 && (
        <div className="h-[220px] w-full">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={merged}>
              <CartesianGrid stroke={gridColor} strokeDasharray="3 3" />
              <XAxis dataKey="at" tickFormatter={formatTick} stroke={axisColor} fontSize={11} />
              <YAxis stroke={axisColor} fontSize={11} width={36} />
              <Tooltip labelFormatter={(v) => formatTick(typeof v === "string" ? v : String(v ?? ""))} />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              <Line type="monotone" dataKey="TEMPERATURE" name="온도(°C)" stroke="#3b82f6" dot={false} connectNulls={false} />
              <Line type="monotone" dataKey="HUMIDITY" name="습도(%)" stroke="#f59e0b" dot={false} connectNulls={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
    </Card>
  );
}
