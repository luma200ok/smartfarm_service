"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { listPrescriptions } from "@/lib/api/prescriptions";
import type { PageResponse, PrescriptionSummaryResponse } from "@/types";

interface PrescriptionHistoryListProps {
  farmId: string;
}

const PAGE_SIZE = 10;
const STATUS_LABELS: Record<string, string> = {
  PENDING: "대기 중",
  PROCESSING: "처리 중",
  COMPLETED: "완료",
  FAILED: "실패",
};

export default function PrescriptionHistoryList({ farmId }: PrescriptionHistoryListProps) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResponse<PrescriptionSummaryResponse> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const res = await listPrescriptions(farmId, page, PAGE_SIZE);
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
  }, [farmId, page]);

  if (error) {
    return <p className="text-sm text-red-600 dark:text-red-400">{error}</p>;
  }

  if (!data) {
    return <p className="text-sm text-zinc-500 dark:text-zinc-400">불러오는 중...</p>;
  }

  return (
    <div className="flex flex-col gap-3">
      <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">처방 이력</h2>
      {data.content.length === 0 ? (
        <p className="text-sm text-zinc-500 dark:text-zinc-400">처방 이력이 없습니다.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {data.content.map((p) => (
            <li key={p.id}>
              <Link
                href={`/farms/${farmId}/prescriptions/${p.id}`}
                className="flex items-center justify-between rounded-md border border-zinc-200 px-4 py-2.5 text-sm hover:bg-zinc-50 dark:border-zinc-800 dark:hover:bg-zinc-900"
              >
                <span className="truncate">{p.question}</span>
                <span className="ml-3 flex shrink-0 items-center gap-2 text-xs text-zinc-400 dark:text-zinc-500">
                  <span className="rounded bg-zinc-100 px-1.5 py-0.5 dark:bg-zinc-800">
                    {STATUS_LABELS[p.status] ?? p.status}
                  </span>
                  {new Date(p.createdAt).toLocaleDateString()}
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
