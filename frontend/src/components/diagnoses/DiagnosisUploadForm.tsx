"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Card, CardTitle } from "@/components/monitoring/ui";
import DiagnosisResultCard from "./DiagnosisResultCard";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { createDiagnosis } from "@/lib/api/diagnoses";
import { getFarm } from "@/lib/api/farms";
import { hasFarmRoleAtLeast } from "@/lib/roles";
import type { DiagnosisResponse, FarmResponse } from "@/types";

interface DiagnosisUploadFormProps {
  farmId: string;
  onUploaded?: () => void;
}

// 진단 이미지 업로드(multipart) → 결과 렌더. contract §3 POST /diagnoses(동기).
// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card·CardTitle)로 통일한다(이슈 #109).
export default function DiagnosisUploadForm({ farmId, onUploaded }: DiagnosisUploadFormProps) {
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<DiagnosisResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [farm, setFarm] = useState<FarmResponse | null>(null);

  // 진단 업로드는 OPERATOR 이상(contract §2, 이슈 #122/#123 리뷰 P2-B) — VIEWER는 폼 자리를
  // 안내 문구로 대체한다. 조회 실패해도 조용히 canWrite=false로 남긴다(보수적으로 숨김).
  useEffect(() => {
    let cancelled = false;
    getFarm(farmId)
      .then((res) => {
        if (!cancelled) setFarm(res);
      })
      .catch(() => {
        // no-op
      });
    return () => {
      cancelled = true;
    };
  }, [farmId]);

  const canWrite = hasFarmRoleAtLeast(farm?.myRole, "OPERATOR");

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    if (!file) {
      setError("이미지를 선택해주세요.");
      return;
    }

    setSubmitting(true);
    try {
      const diagnosis = await createDiagnosis(farmId, file);
      setResult(diagnosis);
      onUploaded?.();
    } catch (err) {
      setError(resolveErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <Card className="p-4">
        {canWrite ? (
          <form onSubmit={handleSubmit} className="flex flex-col gap-3">
            <CardTitle as="h2">작물 이미지 진단</CardTitle>
            <input
              type="file"
              accept="image/*"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              className="text-sm text-dp-body"
            />
            {error && <p className="text-sm text-dp-red-ink">{error}</p>}
            <button
              type="submit"
              disabled={submitting || !file}
              className="self-start rounded-md bg-dp-ink px-4 py-2 text-sm font-medium text-dp-surface transition-colors hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
            >
              {submitting ? "진단 중..." : "진단하기"}
            </button>
          </form>
        ) : (
          <>
            <CardTitle as="h2">작물 이미지 진단</CardTitle>
            <p className="mt-2 text-sm text-dp-faint">조회 전용 역할입니다.</p>
          </>
        )}
      </Card>
      {result && <DiagnosisResultCard diagnosis={result} />}
    </div>
  );
}
