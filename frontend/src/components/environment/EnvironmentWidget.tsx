"use client";

import { useEffect, useState } from "react";
import { DEVICE_LABELS } from "@/constants";
import { getTodayEnvironment } from "@/lib/api/environment";
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
    return (
      <section className="rounded-lg border border-zinc-200 p-4 text-sm text-zinc-500 dark:border-zinc-800 dark:text-zinc-400">
        환경 데이터 불러오는 중...
      </section>
    );
  }

  if (unavailable || !data) {
    return (
      <section className="rounded-lg border border-zinc-200 p-4 text-sm text-zinc-500 dark:border-zinc-800 dark:text-zinc-400">
        환경 데이터를 일시적으로 불러올 수 없습니다.
      </section>
    );
  }

  return (
    <section className="flex flex-col gap-3 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">오늘의 환경</h3>
        <div className="flex items-center gap-2">
          {data.demo && (
            <span className="rounded bg-amber-100 px-2 py-0.5 text-xs text-amber-700 dark:bg-amber-900/40 dark:text-amber-300">
              공용 데모 온실 데이터
            </span>
          )}
          <span className="text-xs text-zinc-400 dark:text-zinc-500">
            {new Date(data.updatedAt).toLocaleString()} 기준
          </span>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 text-sm">
        <div className="rounded-md bg-zinc-50 p-3 dark:bg-zinc-900">
          <p className="text-xs text-zinc-400 dark:text-zinc-500">외기</p>
          {data.outdoor ? (
            <p className="text-zinc-900 dark:text-zinc-50">
              {formatTemp(data.outdoor.temp)} · {formatHumidity(data.outdoor.humidity)}
            </p>
          ) : (
            <p className="text-zinc-400 dark:text-zinc-500">데이터 없음</p>
          )}
        </div>
        <div className="rounded-md bg-zinc-50 p-3 dark:bg-zinc-900">
          <p className="text-xs text-zinc-400 dark:text-zinc-500">
            내부{data.indoor?.controlled ? " (제어중)" : ""}
          </p>
          {data.indoor ? (
            <p className="text-zinc-900 dark:text-zinc-50">
              {formatTemp(data.indoor.temp)} · {formatHumidity(data.indoor.humidity)}
            </p>
          ) : (
            <p className="text-zinc-400 dark:text-zinc-500">데이터 없음</p>
          )}
        </div>
      </div>

      {data.devices.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {data.devices.map((device) => (
            <span
              key={device.name}
              className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                device.on
                  ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300"
                  : "bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400"
              }`}
            >
              {DEVICE_LABELS[device.name] ?? device.name}
              {device.on ? " · 가동중" : ""}
            </span>
          ))}
        </div>
      )}

      {data.alerts.length > 0 && (
        <ul className="flex flex-col gap-1 text-xs text-amber-700 dark:text-amber-300">
          {data.alerts.map((alert) => (
            <li key={alert}>⚠ {alert}</li>
          ))}
        </ul>
      )}
    </section>
  );
}
