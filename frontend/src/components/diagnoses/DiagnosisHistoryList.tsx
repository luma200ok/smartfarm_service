"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { listDiagnoses } from "@/lib/api/diagnoses";
import type { PageResponse, DiagnosisSummaryResponse } from "@/types";

interface DiagnosisHistoryListProps {
  farmId: string;
  refreshKey?: number;
}

const PAGE_SIZE = 10;

export default function DiagnosisHistoryList({ farmId, refreshKey }: DiagnosisHistoryListProps) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResponse<DiagnosisSummaryResponse> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setPage(0);
  }, [refreshKey]);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    listDiagnoses(farmId, page, PAGE_SIZE)
      .then((res) => {
        if (!cancelled) setData(res);
      })
      .catch((err) => {
        if (!cancelled) setError(resolveErrorMessage(err));
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [farmId, page, refreshKey]);

  if (error) {
    return <p className="text-sm text-red-600 dark:text-red-400">{error}</p>;
  }

  if (!data) {
    return <p className="text-sm text-zinc-500 dark:text-zinc-400">불러오는 중...</p>;
  }

  return (
    <div className="flex flex-col gap-3">
      <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">진단 이력</h2>
      {data.content.length === 0 ? (
        <p className="text-sm text-zinc-500 dark:text-zinc-400">진단 이력이 없습니다.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {data.content.map((d) => (
            <li key={d.id}>
              <Link
                href={`/farms/${farmId}/diagnoses/${d.id}`}
                className="flex items-center justify-between rounded-md border border-zinc-200 px-4 py-2.5 text-sm hover:bg-zinc-50 dark:border-zinc-800 dark:hover:bg-zinc-900"
              >
                <span>
                  {d.status === "ood_blocked" ? "진단 불가" : d.labelKr}{" "}
                  <span className="text-zinc-400 dark:text-zinc-500">({d.part})</span>
                </span>
                <span className="text-xs text-zinc-400 dark:text-zinc-500">
                  {new Date(d.createdAt).toLocaleDateString()}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
      {data.totalPages > 1 && (
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
    </div>
  );
}
