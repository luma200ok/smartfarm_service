"use client";

import { useEffect, useState } from "react";
import { Card, CardTitle } from "@/components/monitoring/ui";
import { DEVICE_LABELS } from "@/constants";
import { getTodayEnvironment } from "@/lib/api/environment";
import MeasuredBadge from "@/components/monitoring/MeasuredBadge";
import type { EnvironmentTodayResponse } from "@/types";

interface EnvironmentWidgetProps {
  farmId: number | string;
}

function formatTemp(temp: number | null): string {
  return temp === null || temp === undefined ? "-" : `${temp.toFixed(1)}℃`;
}

function formatHumidity(humidity: number | null): string {
  return humidity === null || humidity === undefined ? "-" : `${humidity.toFixed(0)}%`;
}

// 환경 대시보드 위젯 — 대시보드 홈·농장 상세 상단 공용(이슈 #22). 진입 시 1회 조회(폴링 없음).
// 데이터 없음(null 필드)·조회 실패(D003 등)는 화면을 깨뜨리지 않고 안내 문구로 대체한다.
// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card·CardTitle, components/monitoring/ui.tsx)로
// 통일한다(이슈 #109). API 호출·상태 구조는 무변경.
export default function EnvironmentWidget({ farmId }: EnvironmentWidgetProps) {
  const [data, setData] = useState<EnvironmentTodayResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setUnavailable(false);
      setData(null);
      try {
        const res = await getTodayEnvironment(farmId);
        if (!cancelled) setData(res);
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
  }, [farmId]);

  if (loading) {
    return <Card as="section" className="p-4 text-sm text-dp-sub">환경 데이터 불러오는 중...</Card>;
  }

  if (unavailable || !data) {
    return <Card as="section" className="p-4 text-sm text-dp-sub">환경 데이터를 일시적으로 불러올 수 없습니다.</Card>;
  }

  return (
    <Card as="section" className="flex flex-col gap-3 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <CardTitle as="h3">오늘의 환경</CardTitle>
          <MeasuredBadge />
        </div>
        <div className="flex items-center gap-2">
          {data.demo && (
            <span className="rounded px-2 py-0.5 text-xs font-medium text-dp-amber-deep bg-dp-amber-tint">
              공용 데모 온실 데이터
            </span>
          )}
          <span className="text-xs text-dp-faint">{new Date(data.updatedAt).toLocaleString()} 기준</span>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 text-sm">
        <div className="rounded-md bg-dp-inset p-3">
          <p className="text-xs text-dp-faint">외기</p>
          {data.outdoor ? (
            <p className="text-dp-ink">
              {formatTemp(data.outdoor.temp)} · {formatHumidity(data.outdoor.humidity)}
            </p>
          ) : (
            <p className="text-dp-faint">데이터 없음</p>
          )}
        </div>
        <div className="rounded-md bg-dp-inset p-3">
          <p className="text-xs text-dp-faint">내부{data.indoor?.controlled ? " (제어중)" : ""}</p>
          {data.indoor ? (
            <p className="text-dp-ink">
              {formatTemp(data.indoor.temp)} · {formatHumidity(data.indoor.humidity)}
            </p>
          ) : (
            <p className="text-dp-faint">데이터 없음</p>
          )}
        </div>
      </div>

      {data.devices.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {data.devices.map((device) => (
            <span
              key={device.name}
              className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                device.on ? "bg-dp-green-tint-2 text-dp-green-ink" : "bg-dp-badge-neutral text-dp-muted"
              }`}
            >
              {DEVICE_LABELS[device.name] ?? device.name}
              {device.on ? " · 가동중" : ""}
            </span>
          ))}
        </div>
      )}

      {data.alerts.length > 0 && (
        <ul className="flex flex-col gap-1 text-xs text-dp-amber-deep">
          {data.alerts.map((alert) => (
            <li key={alert}>⚠ {alert}</li>
          ))}
        </ul>
      )}
    </Card>
  );
}
