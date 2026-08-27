"use client";

import { useEffect, useRef, useState, type KeyboardEvent } from "react";
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
import { Card, CardTitle } from "@/components/monitoring/ui";
import { ENV_HISTORY_RANGE_LABELS } from "@/constants";
import { getEnvironmentHistory } from "@/lib/api/environment";
import { useIsDarkMode } from "@/lib/useIsDarkMode";
import { useIsMobileViewport } from "@/lib/useIsMobileViewport";
import type { EnvironmentHistoryPoint, EnvironmentHistoryRange } from "@/types";

interface EnvironmentHistoryChartProps {
  farmId: number | string;
}

const RANGES: EnvironmentHistoryRange[] = ["24h", "7d", "30d"];

function formatTick(
  capturedAt: string,
  range: EnvironmentHistoryRange,
): string {
  const date = new Date(capturedAt);
  if (Number.isNaN(date.getTime())) return "";
  return range === "24h"
    ? date.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })
    : date.toLocaleDateString("ko-KR", { month: "2-digit", day: "2-digit" });
}

function formatTooltipLabel(capturedAt: string): string {
  const date = new Date(capturedAt);
  return Number.isNaN(date.getTime())
    ? capturedAt
    : date.toLocaleString("ko-KR");
}

// recharts XAxis의 interval은 "몇 개씩 건너뛸지"다(0=전부 표시) — 목표 라벨 개수에서 역산한다.
function computeTickInterval(
  pointCount: number,
  range: EnvironmentHistoryRange,
): number | undefined {
  const target = range === "24h" ? 3 : range === "7d" ? 4 : null;
  if (target === null || pointCount <= target) return undefined;
  return Math.max(0, Math.ceil(pointCount / target) - 1);
}

// 환경 시계열 차트(이슈 #53, 다함 벤치마킹 1) — 대시보드 환경 섹션(EnvironmentWidget) 하단 확장.
// 기간 탭(24h/7d/30d) 전환마다 재조회. 다운샘플은 서버가 수행(contract §4.6) —
// FE는 받은 point만 그대로 그리고 null 구간은 선을 끊는다(connectNulls=false).
// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card·CardTitle·Chip)로 통일한다(이슈 #109).
export default function EnvironmentHistoryChart({
  farmId,
}: EnvironmentHistoryChartProps) {
  const [range, setRange] = useState<EnvironmentHistoryRange>("24h");
  const [points, setPoints] = useState<EnvironmentHistoryPoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [unavailable, setUnavailable] = useState(false);
  const isDark = useIsDarkMode();
  // 반응형 마무리(이슈 #147 §3, 시안 README "그래프는 축 라벨 개수를 줄인다") — 모바일에서
  // 7일 기간은 라벨 4개, 24시간 기간은 3개만 남긴다. 30일은 시안에 수치가 없어 기존 동작
  // (recharts 기본 interval) 그대로 둔다. 데스크톱·태블릿은 항상 기존 동작 그대로.
  const isMobile = useIsMobileViewport();
  const tickInterval =
    isMobile && points.length > 0
      ? computeTickInterval(points.length, range)
      : undefined;
  const tabRefs = useRef<
    Record<EnvironmentHistoryRange, HTMLButtonElement | null>
  >({
    "24h": null,
    "7d": null,
    "30d": null,
  });

  useEffect(() => {
    // range/farmId 전환마다 이전 요청을 취소한다(리뷰 픽스 #53 P2-2) — 탭을 빠르게 넘기면
    // 늦게 도착하는 옛 응답이 최신 탭 화면을 덮어쓰는 걸 막는다.
    const controller = new AbortController();

    async function load() {
      setLoading(true);
      setUnavailable(false);
      try {
        const res = await getEnvironmentHistory(farmId, range, {
          signal: controller.signal,
        });
        if (controller.signal.aborted) return;
        setPoints(res.points);
        setLoading(false);
      } catch {
        // client.ts의 api()는 fetch 실패(네트워크 오류·AbortError 포함)를 전부 ApiError(0, ...)로
        // 감싸 원본 에러 타입을 잃으므로, 우리가 직접 abort()한 요청인지는 signal로만 판별 가능하다.
        // 취소는 실패가 아니므로 unavailable 처리하지 않는다.
        if (controller.signal.aborted) return;
        setUnavailable(true);
        setLoading(false);
      }
    }

    load();
    return () => {
      controller.abort();
    };
  }, [farmId, range]);

  // 기간 탭 좌우 화살표 이동(리뷰 픽스 #53 P3) — WAI-ARIA tablist manual/automatic activation
  // 관례대로 이동과 동시에 선택도 바뀐다(포커스+선택 동시 전환), 끝에서는 반대편으로 순환한다.
  function handleTabKeyDown(
    e: KeyboardEvent<HTMLButtonElement>,
    index: number,
  ) {
    if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return;
    e.preventDefault();
    const delta = e.key === "ArrowRight" ? 1 : -1;
    const nextRange = RANGES[(index + delta + RANGES.length) % RANGES.length];
    setRange(nextRange);
    tabRefs.current[nextRange]?.focus();
  }

  const gridColor = isDark ? "#3f3f46" : "#e4e4e7";
  const axisColor = isDark ? "#a1a1aa" : "#71717a";
  const tooltipStyle = {
    backgroundColor: isDark ? "#18181b" : "#ffffff",
    borderColor: isDark ? "#3f3f46" : "#e4e4e7",
    fontSize: 12,
  };

  return (
    <Card as="section" className="flex flex-col gap-4 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <CardTitle as="h3">환경 추이</CardTitle>
        <div role="tablist" aria-label="조회 기간" className="flex gap-1.5">
          {RANGES.map((r, i) => (
            <button
              key={r}
              ref={(el) => {
                tabRefs.current[r] = el;
              }}
              type="button"
              role="tab"
              aria-selected={range === r}
              tabIndex={range === r ? 0 : -1}
              onClick={() => setRange(r)}
              onKeyDown={(e) => handleTabKeyDown(e, i)}
              className={`rounded-md px-[11px] py-1.5 text-[11.5px] leading-none font-medium whitespace-nowrap transition-colors ${
                range === r
                  ? "bg-dp-ink font-semibold text-dp-surface"
                  : "border border-dp-line-strong bg-dp-surface text-dp-body"
              }`}
            >
              {ENV_HISTORY_RANGE_LABELS[r]}
            </button>
          ))}
        </div>
      </div>

      {loading && (
        <p className="text-sm text-dp-sub">차트 데이터 불러오는 중...</p>
      )}

      {!loading && unavailable && (
        <p className="text-sm text-dp-sub">환경 추이를 불러올 수 없습니다.</p>
      )}

      {!loading && !unavailable && points.length === 0 && (
        <p className="text-sm text-dp-sub">표시할 데이터가 없습니다.</p>
      )}

      {!loading && !unavailable && points.length > 0 && (
        <div className="flex flex-col gap-6">
          <div>
            <p className="mb-1 text-xs text-dp-faint">온도(℃)</p>
            <div className="h-56 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={points}>
                  <CartesianGrid stroke={gridColor} strokeDasharray="3 3" />
                  <XAxis
                    dataKey="capturedAt"
                    tickFormatter={(v: string) => formatTick(v, range)}
                    stroke={axisColor}
                    fontSize={11}
                    interval={tickInterval}
                  />
                  <YAxis stroke={axisColor} fontSize={11} width={36} />
                  <Tooltip
                    labelFormatter={(v) =>
                      formatTooltipLabel(
                        typeof v === "string" ? v : String(v ?? ""),
                      )
                    }
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
            <p className="mb-1 text-xs text-dp-faint">습도(%)</p>
            <div className="h-56 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={points}>
                  <CartesianGrid stroke={gridColor} strokeDasharray="3 3" />
                  <XAxis
                    dataKey="capturedAt"
                    tickFormatter={(v: string) => formatTick(v, range)}
                    stroke={axisColor}
                    fontSize={11}
                    interval={tickInterval}
                  />
                  <YAxis
                    stroke={axisColor}
                    fontSize={11}
                    width={36}
                    domain={[0, 100]}
                  />
                  <Tooltip
                    labelFormatter={(v) =>
                      formatTooltipLabel(
                        typeof v === "string" ? v : String(v ?? ""),
                      )
                    }
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
    </Card>
  );
}
