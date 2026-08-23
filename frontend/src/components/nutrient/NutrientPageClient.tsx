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
      <NutrientCalculator farmId={farmId} onSaved={() => setRefreshKey((k) => k + 1)} />
      <NutrientRecipeList farmId={farmId} refreshKey={refreshKey} />
    </main>
  );
}
