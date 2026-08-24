"use client";

import { useEffect, useState } from "react";
import { Card, CardTitle } from "@/components/monitoring/ui";
import { getTodayEnvironment } from "@/lib/api/environment";
import {
  classifyVpdLevel,
  dewPointC,
  TOMATO_VPD_RANGE_KPA,
  vaporPressureDeficitKpa,
  type VpdLevel,
} from "@/lib/vpd";
import type { EnvironmentTodayResponse } from "@/types";

interface VpdWidgetProps {
  farmId: number | string;
}

const LEVEL_LABELS: Record<VpdLevel, string> = {
  low: "낮음 (과습 위험)",
  optimal: "적정",
  high: "높음 (수분 스트레스 위험)",
};

const LEVEL_STYLES: Record<VpdLevel, string> = {
  low: "bg-dp-blue-tint text-dp-blue-ink",
  optimal: "bg-dp-green-tint-2 text-dp-green-ink",
  high: "bg-dp-amber-tint text-dp-amber-deep",
};

// VPD·이슬점 위젯(contract §4.8, 이슈 #57) — BE 없음, environment/today의 현재 내부 온습도를
// 입력으로 FE 순수 계산(lib/vpd.ts, 계산 근거·출처는 해당 파일 주석 참조).
// 각 탭 페이지가 독립적으로 데이터를 조회하는 관례(과설계 금지)에 따라 이 위젯도
// EnvironmentWidget과 별도로 environment/today를 자체 조회한다.
// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card·CardTitle)로 통일한다(이슈 #109).
export default function VpdWidget({ farmId }: VpdWidgetProps) {
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
    return <Card as="section" className="p-4 text-sm text-dp-sub">VPD 계산 중...</Card>;
  }

  const temp = data?.indoor?.temp;
  const humidity = data?.indoor?.humidity;
  const canCalculate = !unavailable && temp !== null && temp !== undefined && humidity !== null && humidity !== undefined;

  if (!canCalculate) {
    return <Card as="section" className="p-4 text-sm text-dp-sub">내부 온습도 데이터가 없어 VPD를 계산할 수 없습니다.</Card>;
  }

  const vpd = vaporPressureDeficitKpa(temp, humidity);
  const dewPoint = dewPointC(temp, humidity);
  const level = classifyVpdLevel(vpd);

  return (
    <Card as="section" className="flex flex-col gap-3 p-4">
      <CardTitle as="h3">VPD · 이슬점</CardTitle>
      <div className="grid grid-cols-2 gap-3 text-sm">
        <div className="rounded-md bg-dp-inset p-3">
          <p className="text-xs text-dp-faint">VPD (포화수증기압차)</p>
          <p className="text-lg font-semibold text-dp-ink">{vpd.toFixed(2)} kPa</p>
          <span className={`mt-1 inline-block rounded px-1.5 py-0.5 text-xs font-medium ${LEVEL_STYLES[level]}`}>
            {LEVEL_LABELS[level]}
          </span>
        </div>
        <div className="rounded-md bg-dp-inset p-3">
          <p className="text-xs text-dp-faint">이슬점</p>
          <p className="text-lg font-semibold text-dp-ink">{dewPoint.toFixed(1)}℃</p>
        </div>
      </div>
      <p className="text-xs text-dp-faint">
        토마토 생육 권장 VPD: {TOMATO_VPD_RANGE_KPA.min}~{TOMATO_VPD_RANGE_KPA.max} kPa (참고용 — 생육단계·품종에 따라
        달라질 수 있습니다)
      </p>
    </Card>
  );
}
