"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import DiagnosisResultCard from "./DiagnosisResultCard";
import { isNotFound, resolveErrorMessage } from "@/lib/api/errorMessage";
import { getDiagnosis } from "@/lib/api/diagnoses";
import type { DiagnosisResponse } from "@/types";

interface DiagnosisDetailProps {
  farmId: string;
  diagnosisId: string;
}

export default function DiagnosisDetail({ farmId, diagnosisId }: DiagnosisDetailProps) {
  const [diagnosis, setDiagnosis] = useState<DiagnosisResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getDiagnosis(farmId, diagnosisId)
      .then((data) => {
        if (!cancelled) setDiagnosis(data);
      })
      .catch((err) => {
        if (cancelled) return;
        if (isNotFound(err)) {
          setNotFound(true);
        } else {
          setError(resolveErrorMessage(err));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [farmId, diagnosisId]);

  if (notFound) {
    return (
      <p className="px-6 py-6 text-sm text-dp-sub">
        진단 이력을 찾을 수 없습니다.{" "}
        <Link href={`/farms/${farmId}/diagnoses`} className="underline">
          이력 목록으로
        </Link>
      </p>
    );
  }

  if (error) {
    return <p className="px-6 py-6 text-sm text-dp-red-ink">{error}</p>;
  }

  if (!diagnosis) {
    return <p className="px-6 py-6 text-sm text-dp-sub">불러오는 중...</p>;
  }

  return (
    <main className="flex flex-col gap-4 px-6 py-6">
      {/* 페이지 제목(이슈 #136 — 구 FarmTabsHeader 제거로 드릴다운 화면도 제목을 잃었다).
          발생 시각은 바로 아래 DiagnosisResultCard가 이미 표시하므로(리뷰 P3-2) 여기서는
          중복하지 않고 "무엇의 상세인지"만 밝힌다. */}
      <h1 className="text-[17px] leading-[1.2] font-bold text-dp-ink">진단 상세</h1>
      <DiagnosisResultCard diagnosis={diagnosis} />
    </main>
  );
}
