"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Card, CardTitle } from "@/components/monitoring/ui";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { listDiagnoses } from "@/lib/api/diagnoses";
import type { PageResponse, DiagnosisSummaryResponse } from "@/types";

interface DiagnosisHistoryListProps {
  farmId: string;
  refreshKey?: number;
}

const PAGE_SIZE = 10;

// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card·CardTitle)로 통일한다(이슈 #109).
export default function DiagnosisHistoryList({ farmId, refreshKey }: DiagnosisHistoryListProps) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResponse<DiagnosisSummaryResponse> | null>(null);
  const [error, setError] = useState<string | null>(null);

  // refreshKey가 바뀌면 페이지를 0으로 되돌린다 (렌더 중 상태 조정 — effect 대신).
  const [trackedRefreshKey, setTrackedRefreshKey] = useState(refreshKey);
  if (refreshKey !== trackedRefreshKey) {
    setTrackedRefreshKey(refreshKey);
    setPage(0);
  }

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const res = await listDiagnoses(farmId, page, PAGE_SIZE);
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

  if (error) {
    return <p className="text-sm text-dp-red-ink">{error}</p>;
  }

  if (!data) {
    return <p className="text-sm text-dp-sub">불러오는 중...</p>;
  }

  return (
    <div className="flex flex-col gap-3">
      <CardTitle size="lg">진단 이력</CardTitle>
      {data.content.length === 0 ? (
        <p className="text-sm text-dp-sub">진단 이력이 없습니다.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {data.content.map((d) => (
            <li key={d.id}>
              <Link href={`/farms/${farmId}/diagnoses/${d.id}`}>
                <Card className="flex items-center justify-between px-4 py-2.5 text-sm hover:bg-dp-inset">
                  <span>
                    {d.status === "ood_blocked" ? "진단 불가" : d.labelKr}{" "}
                    <span className="text-dp-faint">({d.part})</span>
                  </span>
                  <span className="text-xs text-dp-faint">{new Date(d.createdAt).toLocaleDateString()}</span>
                </Card>
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
            className="rounded-md border border-dp-line-strong px-2 py-1 text-dp-body disabled:opacity-40"
          >
            이전
          </button>
          <span className="text-dp-sub">
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
    </div>
  );
}
