"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
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
    <section className="flex flex-col gap-4 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
      <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">저장된 레시피</h2>

      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
      {!data && !error && <p className="text-sm text-zinc-500 dark:text-zinc-400">불러오는 중...</p>}
      {data && data.content.length === 0 && (
        <p className="text-sm text-zinc-500 dark:text-zinc-400">저장된 레시피가 없습니다.</p>
      )}

      {data && data.content.length > 0 && (
        <ul className="flex flex-col gap-2">
          {data.content.map((recipe) => (
            <li key={recipe.id}>
              <Link
                href={`/farms/${farmId}/nutrient/${recipe.id}`}
                className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-zinc-200 px-4 py-3 text-sm transition-colors hover:border-zinc-400 dark:border-zinc-800 dark:hover:border-zinc-600"
              >
                <div className="flex items-center gap-2">
                  <span className="font-medium text-zinc-900 dark:text-zinc-50">{recipe.name}</span>
                  <span className="rounded bg-zinc-100 px-1.5 py-0.5 text-xs text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">
                    {NUTRIENT_STAGE_LABELS[recipe.stage] ?? recipe.stage}
                  </span>
                </div>
                <span className="text-xs text-zinc-500 dark:text-zinc-400">
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
            className="rounded-md border border-zinc-300 px-2 py-1 disabled:opacity-40 dark:border-zinc-700"
          >
            이전
          </button>
          <span className="text-zinc-500 dark:text-zinc-400">
            {data.page + 1} / {data.totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= data.totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-md border border-zinc-300 px-2 py-1 disabled:opacity-40 dark:border-zinc-700"
          >
            다음
          </button>
        </div>
      )}
    </section>
  );
}
