"use client";

import { useEffect, useState } from "react";
import { CardTitle } from "@/components/monitoring/ui";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { listSystemLogs } from "@/lib/api/systemLogs";
import type { PageResponse, SystemLogResponse } from "@/types";

interface SystemLogPanelProps {
  farmId: string;
}

const PAGE_SIZE = 8;

function formatOccurredAt(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const now = new Date();
  const sameDay =
    date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth() && date.getDate() === now.getDate();
  return sameDay
    ? date.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })
    : date.toLocaleDateString("ko-KR", { month: "2-digit", day: "2-digit" });
}

// 시스템 로그 위젯(시안 06, 이슈 #129 §4.17) — 관리(장비·센서) 화면 우측 하단에 붙는 축약 패널.
// append-only 조회 전용이라 필터·수정 UI는 없다. actorId는 nullable에 닉네임이 실리지 않으므로
// message(서버가 이미 완결된 문장으로 기록)만 시각과 함께 보여준다 — 이름을 지어내지 않는다
// (이슈 #136 알람 타임라인과 동일 원칙). 전용 "전체 로그" 페이지가 아직 없어(disabled nav 항목)
// 새 라우트를 만드는 대신 위젯 안에서 "더 보기"로 다음 페이지를 이어붙인다.
export default function SystemLogPanel({ farmId }: SystemLogPanelProps) {
  const [items, setItems] = useState<SystemLogResponse[]>([]);
  const [page, setPage] = useState<PageResponse<SystemLogResponse> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setItems([]);
      setPage(null);
      setError(null);
      try {
        const res = await listSystemLogs(farmId, { page: 0, size: PAGE_SIZE });
        if (cancelled) return;
        setItems(res.content);
        setPage(res);
      } catch (err) {
        if (!cancelled) setError(resolveErrorMessage(err));
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [farmId]);

  async function handleLoadMore() {
    if (!page || loadingMore) return;
    setLoadingMore(true);
    try {
      const res = await listSystemLogs(farmId, { page: page.page + 1, size: PAGE_SIZE });
      setItems((prev) => [...prev, ...res.content]);
      setPage(res);
    } catch (err) {
      setError(resolveErrorMessage(err));
    } finally {
      setLoadingMore(false);
    }
  }

  return (
    <div className="flex flex-col rounded-[10px] border border-dp-line bg-dp-surface p-4">
      <CardTitle as="h3">시스템 로그</CardTitle>

      <div className="mt-2.5 max-h-[360px] overflow-y-auto">
        {error && <p className="text-sm text-dp-red-ink">{error}</p>}
        {!error && page === null && <p className="text-sm text-dp-sub">불러오는 중...</p>}
        {!error && page !== null && items.length === 0 && <p className="text-sm text-dp-sub">기록된 로그가 없습니다.</p>}
        {!error && items.length > 0 && (
          <ul>
            {items.map((log) => (
              <li key={log.id} className="flex gap-2.5 border-b border-dp-line-row py-[7px] last:border-0">
                <span className="w-[42px] flex-none font-mono text-[11px] font-medium text-dp-muted">
                  {formatOccurredAt(log.occurredAt)}
                </span>
                <span className="flex-1 text-xs font-medium text-dp-ink">{log.message}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {page && page.page + 1 < page.totalPages && (
        <button
          type="button"
          onClick={handleLoadMore}
          disabled={loadingMore}
          className="mt-2.5 flex-none rounded-md border border-dp-line-strong py-2 text-center text-xs font-semibold text-dp-body disabled:opacity-60"
        >
          {loadingMore ? "불러오는 중..." : "더 보기"}
        </button>
      )}
    </div>
  );
}
