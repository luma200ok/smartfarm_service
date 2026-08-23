"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import FarmRackPanel from "@/components/dashboard/FarmRackPanel";
import FarmStatusCard from "@/components/dashboard/FarmStatusCard";
import FarmTrendChart from "@/components/dashboard/FarmTrendChart";
import DashboardHeader from "@/components/layout/DashboardHeader";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { listFarms } from "@/lib/api/farms";
import type { FarmSummaryResponse } from "@/types";

// 대시보드 홈(이슈 #99) — 새 디자인 + 실 API로 교체. 목업 브리핑/할일/저장한 분석 등
// 대응 API 없는 항목은 뺐다(handoff 요건 2). 농장 카드는 §4.10 존·랙 구조 + 장비 요약,
// 하단 패널은 선택한 농장의 랙 도면(§4.11 readings/latest)·24시간 추이(readings/series)다.
export default function DashboardHome() {
  const [farms, setFarms] = useState<FarmSummaryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selectedFarmId, setSelectedFarmId] = useState<number | null>(null);

  useEffect(() => {
    listFarms()
      .then((data) => {
        setFarms(data);
        setSelectedFarmId((prev) => prev ?? (data.length > 0 ? data[0].id : null));
      })
      .catch((err) => setError(resolveErrorMessage(err)));
  }, []);

  const selectedFarm = farms?.find((f) => f.id === selectedFarmId) ?? null;

  return (
    <div className="flex flex-1 flex-col">
      {/* 데스크톱은 Sidebar(이슈 #42)가 로고를 상시 노출하므로 헤더가 비어 보이지 않게 title을 전달 */}
      <DashboardHeader title="대시보드" />
      <main className="flex flex-col gap-4 bg-dp-canvas px-4 py-4 min-[768px]:px-5 min-[1440px]:px-6 min-[1440px]:py-5">
        {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

        {!error && farms === null && (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">불러오는 중...</p>
        )}

        {!error && farms !== null && farms.length === 0 && (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            아직 등록된 농장이 없습니다.{" "}
            <Link href="/farms" className="underline">
              농장을 만들거나 초대코드로 합류
            </Link>
            해보세요.
          </p>
        )}

        {!error && farms !== null && farms.length > 0 && (
          <>
            <div className="grid grid-cols-1 gap-3.5 min-[640px]:grid-cols-2 min-[1440px]:grid-cols-4">
              {farms.map((farm) => (
                <FarmStatusCard
                  key={farm.id}
                  farm={farm}
                  selected={farm.id === selectedFarmId}
                  onSelect={() => setSelectedFarmId(farm.id)}
                />
              ))}
            </div>

            {selectedFarm && (
              <div className="grid gap-3.5 min-[1440px]:grid-cols-[1.3fr_1fr]">
                <FarmRackPanel farmId={selectedFarm.id} farmName={selectedFarm.name} />
                <FarmTrendChart farmId={selectedFarm.id} />
              </div>
            )}
          </>
        )}
      </main>
    </div>
  );
}
