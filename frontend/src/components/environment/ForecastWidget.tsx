"use client";

import { useEffect, useState } from "react";
import { Card, CardTitle } from "@/components/monitoring/ui";
import { WEATHER_SKY_LABELS } from "@/constants";
import { getForecast } from "@/lib/api/environment";
import type { ForecastPoint, ForecastResponse, WeatherSky } from "@/types";

interface ForecastWidgetProps {
  farmId: number | string;
}

const SKY_ICONS: Record<WeatherSky, string> = {
  SUNNY: "☀️",
  CLOUDY: "⛅",
  OVERCAST: "☁️",
};

function formatHour(time: string): string {
  const date = new Date(time);
  if (Number.isNaN(date.getTime())) return time;
  return date.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
}

function formatTemp(temp: number | null | undefined): string {
  return temp === null || temp === undefined ? "-" : `${temp.toFixed(0)}℃`;
}

function formatPop(pop: number | null | undefined): string {
  // pop이 소수로 올 수 있어(리뷰 픽스 #57 P3-3) 정수 퍼센트로 반올림해 표시한다.
  return pop === null || pop === undefined ? "-" : `${Math.round(pop)}%`;
}

// 날씨예보 위젯(contract §4.8, 이슈 #57) — 향후 24h·1시간 간격 KMA 단기예보를
// 가로 스크롤 카드 목록으로 보여준다. 서버 키 미등록 등으로 W001(502)이 자주
// 발생할 수 있는 경로라 실패 시 화면을 깨뜨리지 않고 안내 문구로 대체한다.
// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card·CardTitle)로 통일한다(이슈 #109).
export default function ForecastWidget({ farmId }: ForecastWidgetProps) {
  const [data, setData] = useState<ForecastResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setUnavailable(false);
      setData(null);
      try {
        const res = await getForecast(farmId);
        if (!cancelled) setData(res);
      } catch {
        // W001(502)·네트워크 오류 등 원인 불문 동일하게 "예보를 불러올 수 없습니다" 안내로 대체.
        if (!cancelled) setUnavailable(true);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [farmId]);

  if (loading) {
    return <Card as="section" className="p-4 text-sm text-dp-sub">예보 불러오는 중...</Card>;
  }

  if (unavailable || !data || data.points.length === 0) {
    return <Card as="section" className="p-4 text-sm text-dp-sub">예보를 불러올 수 없습니다.</Card>;
  }

  return (
    <Card as="section" className="flex flex-col gap-3 p-4">
      <div className="flex items-center justify-between">
        <CardTitle as="h3">날씨예보</CardTitle>
        <span className="text-xs text-dp-faint">{new Date(data.updatedAt).toLocaleString("ko-KR")} 기준</span>
      </div>
      <div className="flex gap-2 overflow-x-auto pb-1">
        {data.points.map((point: ForecastPoint) => (
          <div
            key={point.time}
            className="flex min-w-[64px] shrink-0 flex-col items-center gap-1 rounded-md bg-dp-inset px-2 py-2 text-center"
          >
            <span className="text-xs text-dp-faint">{formatHour(point.time)}</span>
            <span className="text-lg leading-none" title={point.sky ? WEATHER_SKY_LABELS[point.sky] : undefined}>
              {point.sky ? SKY_ICONS[point.sky] : "-"}
            </span>
            <span className="text-sm font-medium text-dp-ink">{formatTemp(point.temp)}</span>
            <span className="text-xs text-dp-blue-ink">{formatPop(point.pop)}</span>
          </div>
        ))}
      </div>
    </Card>
  );
}
