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
    <main className="px-6 py-6">
      <DiagnosisResultCard diagnosis={diagnosis} />
    </main>
  );
}
