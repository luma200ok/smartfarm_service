"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { Card, CardTitle } from "@/components/monitoring/ui";
import { VALIDATION } from "@/constants";
import { isPrescriptionLimitExceeded, resolveErrorMessage } from "@/lib/api/errorMessage";
import { listDiagnoses } from "@/lib/api/diagnoses";
import { createPrescription } from "@/lib/api/prescriptions";
import type { DiagnosisSummaryResponse } from "@/types";

interface PrescriptionCreateFormProps {
  farmId: string;
}

// 처방 질문 입력(+선택: 진단 이력 연결) → 202 응답 즉시 상세 페이지로 이동해 폴링한다.
// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card·CardTitle)로 통일한다(이슈 #109).
export default function PrescriptionCreateForm({ farmId }: PrescriptionCreateFormProps) {
  const router = useRouter();
  const [question, setQuestion] = useState("");
  const [diagnosisId, setDiagnosisId] = useState<string>("");
  const [diagnoses, setDiagnoses] = useState<DiagnosisSummaryResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  // P004(처방 대기 한도 초과) 전용 — 재시도 유도 문구(error)와 달리 한도 안내로 별도 렌더한다.
  const [limitExceeded, setLimitExceeded] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    listDiagnoses(farmId, 0, 50)
      .then((res) => setDiagnoses(res.content.filter((d) => d.status === "ok")))
      .catch(() => {
        // 진단 이력 로딩 실패는 처방 작성 자체를 막지 않는다 — 선택 옵션만 비운다.
      });
  }, [farmId]);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    setLimitExceeded(null);

    if (
      question.length < VALIDATION.prescriptionQuestion.minLength ||
      question.length > VALIDATION.prescriptionQuestion.maxLength
    ) {
      setError(`질문은 ${VALIDATION.prescriptionQuestion.maxLength}자 이하로 입력해주세요.`);
      return;
    }

    setSubmitting(true);
    try {
      const prescription = await createPrescription(farmId, {
        question,
        diagnosisId: diagnosisId ? Number(diagnosisId) : undefined,
      });
      router.push(`/farms/${farmId}/prescriptions/${prescription.id}`);
    } catch (err) {
      if (isPrescriptionLimitExceeded(err)) {
        setLimitExceeded(resolveErrorMessage(err));
      } else {
        setError(resolveErrorMessage(err));
      }
      setSubmitting(false);
    }
  }

  return (
    <Card className="p-4">
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        <CardTitle as="h2">처방 요청</CardTitle>

        {diagnoses.length > 0 && (
          <div className="flex flex-col gap-1.5">
            <label htmlFor="diagnosis-select" className="text-sm font-medium text-dp-body">
              관련 진단 이력 (선택)
            </label>
            <select
              id="diagnosis-select"
              value={diagnosisId}
              onChange={(e) => setDiagnosisId(e.target.value)}
              className="rounded-md border border-dp-line-strong bg-dp-surface px-3 py-2 text-sm text-dp-ink outline-none focus:border-dp-line-strong focus:ring-1 focus:ring-dp-line-strong"
            >
              <option value="">선택 안 함</option>
              {diagnoses.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.labelKr} ({new Date(d.createdAt).toLocaleDateString()})
                </option>
              ))}
            </select>
          </div>
        )}

        <div className="flex flex-col gap-1.5">
          <label htmlFor="question" className="text-sm font-medium text-dp-body">
            질문
          </label>
          <textarea
            id="question"
            required
            maxLength={VALIDATION.prescriptionQuestion.maxLength}
            rows={4}
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            className="rounded-md border border-dp-line-strong bg-dp-surface px-3 py-2 text-sm text-dp-ink outline-none focus:border-dp-line-strong focus:ring-1 focus:ring-dp-line-strong"
          />
        </div>

        {limitExceeded && (
          <p className="rounded-md bg-dp-amber-tint px-3 py-2 text-sm text-dp-amber-deep">{limitExceeded}</p>
        )}
        {error && <p className="text-sm text-dp-red-ink">{error}</p>}

        <button
          type="submit"
          disabled={submitting}
          className="self-start rounded-md bg-dp-ink px-4 py-2 text-sm font-medium text-dp-surface transition-colors hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {submitting ? "요청 중..." : "처방 요청"}
        </button>
      </form>
    </Card>
  );
}
