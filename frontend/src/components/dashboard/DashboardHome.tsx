"use client";

import Link from "next/link";
import { useState } from "react";
import EnvironmentWidget from "@/components/environment/EnvironmentWidget";
import FarmSummaryList from "@/components/farms/FarmSummaryList";
import DashboardHeader from "@/components/layout/DashboardHeader";
import type { FarmSummaryResponse } from "@/types";

// 대시보드 홈 — 내 농장 목록 요약 + 환경 위젯(이슈 #22). 환경 데이터는 전 농장 공용이라
// 내 농장 중 첫 번째(FarmSummaryList가 넘겨주는 목록 순서)를 대표로 조회한다.
export default function DashboardHome() {
  const [firstFarmId, setFirstFarmId] = useState<number | null>(null);

  function handleFarmsLoaded(farms: FarmSummaryResponse[]) {
    setFirstFarmId(farms.length > 0 ? farms[0].id : null);
  }

  return (
    <div className="flex flex-1 flex-col">
      <DashboardHeader />
      <main className="flex flex-col gap-6 px-6 py-6">
        {firstFarmId !== null && <EnvironmentWidget farmId={firstFarmId} />}

        <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">내 농장</h2>
        <FarmSummaryList
          onLoaded={handleFarmsLoaded}
          emptyHint={
            <>
              아직 등록된 농장이 없습니다.{" "}
              <Link href="/farms" className="underline">
                농장을 만들거나 초대코드로 합류
              </Link>
              해보세요.
            </>
          }
        />
      </main>
    </div>
  );
}
