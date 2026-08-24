"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Card, CardTitle, StatusBadge } from "@/components/monitoring/ui";
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

// 처방 상태 → StatusBadge 톤. PENDING/PROCESSING은 진행 중이라 neutral, COMPLETED는 done,
// FAILED는 critical로 표기한다(ZoneControlPanel의 장비 상태 톤 매핑과 같은 원칙, 이슈 #109).
function statusTone(status: string): "done" | "warning" | "neutral" | "critical" {
  switch (status) {
    case "COMPLETED":
      return "done";
    case "FAILED":
      return "critical";
    default:
      return "neutral";
  }
}

// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card·CardTitle·StatusBadge)로 통일한다(이슈 #109).
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
    return <p className="text-sm text-dp-red-ink">{error}</p>;
  }

  if (!data) {
    return <p className="text-sm text-dp-sub">불러오는 중...</p>;
  }

  return (
    <div className="flex flex-col gap-3">
      <CardTitle size="lg">처방 이력</CardTitle>
      {data.content.length === 0 ? (
        <p className="text-sm text-dp-sub">처방 이력이 없습니다.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {data.content.map((p) => (
            <li key={p.id}>
              <Link href={`/farms/${farmId}/prescriptions/${p.id}`}>
                <Card className="flex items-center justify-between px-4 py-2.5 text-sm hover:bg-dp-inset">
                  <span className="truncate text-dp-ink">{p.question}</span>
                  <span className="ml-3 flex shrink-0 items-center gap-2 text-xs text-dp-faint">
                    <StatusBadge label={STATUS_LABELS[p.status] ?? p.status} tone={statusTone(p.status)} />
                    {new Date(p.createdAt).toLocaleDateString()}
                  </span>
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
