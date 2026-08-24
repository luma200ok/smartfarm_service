"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Card, StatusBadge } from "@/components/monitoring/ui";
import { ROLE_LABELS } from "@/constants";
import { listFarms } from "@/lib/api/farms";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import type { FarmSummaryResponse } from "@/types";

const CROP_LABELS: Record<string, string> = { TOMATO: "토마토" };

interface FarmSummaryListProps {
  emptyHint?: React.ReactNode;
  // 목록 조회 완료(성공) 시 상위 컴포넌트에 결과를 전달 — 대시보드 홈의 환경 위젯이 별도
  // listFarms() 호출 없이 같은 결과를 재사용하도록(이슈 #22, 중복 조회 방지).
  onLoaded?: (farms: FarmSummaryResponse[]) => void;
}

// 내 농장 목록 요약 카드 — 대시보드 홈·농장 목록 화면에서 공용으로 쓴다.
// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card)로 통일한다(이슈 #109).
export default function FarmSummaryList({ emptyHint, onLoaded }: FarmSummaryListProps) {
  const [farms, setFarms] = useState<FarmSummaryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    listFarms()
      .then((data) => {
        if (!cancelled) {
          setFarms(data);
          onLoaded?.(data);
        }
      })
      .catch((err) => {
        if (!cancelled) setError(resolveErrorMessage(err));
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (error) {
    return <p className="text-sm text-dp-red-ink">{error}</p>;
  }

  if (farms === null) {
    return <p className="text-sm text-dp-sub">불러오는 중...</p>;
  }

  if (farms.length === 0) {
    return <p className="text-sm text-dp-sub">{emptyHint ?? "아직 등록된 농장이 없습니다."}</p>;
  }

  return (
    <ul className="flex flex-col gap-2">
      {farms.map((farm) => (
        <li key={farm.id}>
          <Link href={`/farms/${farm.id}`}>
            <Card className="flex items-center justify-between px-4 py-3 text-sm transition-colors hover:bg-dp-inset">
              <span className="font-medium text-dp-ink">{farm.name}</span>
              <span className="flex items-center gap-2 text-dp-sub">
                <span>{CROP_LABELS[farm.cropType] ?? farm.cropType}</span>
                <StatusBadge
                  label={ROLE_LABELS[farm.myRole] ?? farm.myRole}
                  tone={farm.myRole === "PENDING" ? "warning" : "neutral"}
                />
              </span>
            </Card>
          </Link>
        </li>
      ))}
    </ul>
  );
}
