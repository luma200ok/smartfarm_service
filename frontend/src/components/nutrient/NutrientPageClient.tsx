"use client";

import { useState } from "react";
import NutrientCalculator from "./NutrientCalculator";
import NutrientRecipeList from "./NutrientRecipeList";

interface NutrientPageClientProps {
  farmId: string;
}

// 양액 탭(이슈 #64·#65) 오케스트레이터 — 계산기(저장 없이 미리보기) + 저장된 레시피 목록.
// 저장 성공 시 refreshKey로 목록을 새로고침한다(DiagnosesPageClient와 동일 관례).
export default function NutrientPageClient({ farmId }: NutrientPageClientProps) {
  const [refreshKey, setRefreshKey] = useState(0);

  return (
    <main className="flex flex-col gap-8 px-6 py-6">
      {/* 페이지 제목(이슈 #136 — 구 FarmTabsHeader 제거로 이 화면엔 제목이 없었다). */}
      <h1 className="text-[17px] leading-[1.2] font-bold text-dp-ink">양액 배합</h1>
      <NutrientCalculator farmId={farmId} onSaved={() => setRefreshKey((k) => k + 1)} />
      <NutrientRecipeList farmId={farmId} refreshKey={refreshKey} />
    </main>
  );
}
