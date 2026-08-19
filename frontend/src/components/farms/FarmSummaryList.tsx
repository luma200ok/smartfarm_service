"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { listFarms } from "@/lib/api/farms";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import type { FarmSummaryResponse } from "@/types";

const CROP_LABELS: Record<string, string> = { TOMATO: "토마토" };
const ROLE_LABELS: Record<string, string> = { OWNER: "관리자", MEMBER: "멤버" };

interface FarmSummaryListProps {
  emptyHint?: React.ReactNode;
}

// 내 농장 목록 요약 카드 — 대시보드 홈·농장 목록 화면에서 공용으로 쓴다.
export default function FarmSummaryList({ emptyHint }: FarmSummaryListProps) {
  const [farms, setFarms] = useState<FarmSummaryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    listFarms()
      .then((data) => {
        if (!cancelled) setFarms(data);
      })
      .catch((err) => {
        if (!cancelled) setError(resolveErrorMessage(err));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (error) {
    return <p className="text-sm text-red-600 dark:text-red-400">{error}</p>;
  }

  if (farms === null) {
    return <p className="text-sm text-zinc-500 dark:text-zinc-400">불러오는 중...</p>;
  }

  if (farms.length === 0) {
    return (
      <p className="text-sm text-zinc-500 dark:text-zinc-400">
        {emptyHint ?? "아직 등록된 농장이 없습니다."}
      </p>
    );
  }

  return (
    <ul className="flex flex-col gap-2">
      {farms.map((farm) => (
        <li key={farm.id}>
          <Link
            href={`/farms/${farm.id}`}
            className="flex items-center justify-between rounded-md border border-zinc-200 px-4 py-3 text-sm transition-colors hover:bg-zinc-50 dark:border-zinc-800 dark:hover:bg-zinc-900"
          >
            <span className="font-medium text-zinc-900 dark:text-zinc-50">{farm.name}</span>
            <span className="flex items-center gap-2 text-zinc-500 dark:text-zinc-400">
              <span>{CROP_LABELS[farm.cropType] ?? farm.cropType}</span>
              <span className="rounded bg-zinc-100 px-2 py-0.5 text-xs dark:bg-zinc-800">
                {ROLE_LABELS[farm.myRole] ?? farm.myRole}
              </span>
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
