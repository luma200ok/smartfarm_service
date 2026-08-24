"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Card, CardTitle, StatusBadge } from "@/components/monitoring/ui";
import { NUTRIENT_STAGE_LABELS } from "@/constants";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { listNutrientRecipes } from "@/lib/api/nutrientRecipes";
import type { NutrientRecipeSummaryResponse, PageResponse } from "@/types";

interface NutrientRecipeListProps {
  farmId: string;
  refreshKey?: number;
}

const PAGE_SIZE = 10;

// 저장된 양액 레시피 목록(최신순, 이슈 #65) — FarmLogList·DiagnosisHistoryList와 동일 관례.
export default function NutrientRecipeList({ farmId, refreshKey }: NutrientRecipeListProps) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResponse<NutrientRecipeSummaryResponse> | null>(null);
  const [error, setError] = useState<string | null>(null);

  // refreshKey가 바뀌면 페이지를 0으로 되돌린다(렌더 중 상태 조정 — effect 대신, DiagnosisHistoryList와 동일).
  const [trackedRefreshKey, setTrackedRefreshKey] = useState(refreshKey);
  if (refreshKey !== trackedRefreshKey) {
    setTrackedRefreshKey(refreshKey);
    setPage(0);
  }

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const res = await listNutrientRecipes(farmId, page, PAGE_SIZE);
        if (!cancelled) {
          setData(res);
          setError(null);
        }
      } catch (err) {
        if (!cancelled) setError(resolveErrorMessage(err));
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [farmId, page, refreshKey]);

  return (
    <Card className="flex flex-col gap-4 px-4 py-4">
      <h2>
        <CardTitle size="lg">저장된 레시피</CardTitle>
      </h2>

      {error && <p className="text-sm text-dp-red-ink">{error}</p>}
      {!data && !error && <p className="text-sm text-dp-muted">불러오는 중...</p>}
      {data && data.content.length === 0 && <p className="text-sm text-dp-muted">저장된 레시피가 없습니다.</p>}

      {data && data.content.length > 0 && (
        <ul className="flex flex-col gap-2">
          {data.content.map((recipe) => (
            <li key={recipe.id}>
              <Link
                href={`/farms/${farmId}/nutrient/${recipe.id}`}
                className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-dp-line px-4 py-3 text-sm hover:border-dp-line-strong"
              >
                <div className="flex items-center gap-2">
                  <span className="font-medium text-dp-ink">{recipe.name}</span>
                  <StatusBadge label={NUTRIENT_STAGE_LABELS[recipe.stage] ?? recipe.stage} tone="neutral" />
                </div>
                <span className="text-xs text-dp-muted">
                  EC {recipe.estimatedEc.toFixed(2)} · {recipe.createdAt.slice(0, 10)}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex items-center gap-3 text-sm">
          <button
            type="button"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="rounded-md border border-dp-line-strong px-2 py-1 text-dp-body disabled:opacity-40"
          >
            이전
          </button>
          <span className="text-dp-muted">
            {data.page + 1} / {data.totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= data.totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-md border border-dp-line-strong px-2 py-1 text-dp-body disabled:opacity-40"
          >
            다음
          </button>
        </div>
      )}
    </Card>
  );
}
