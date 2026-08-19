"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { VALIDATION } from "@/constants";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { listDiagnoses } from "@/lib/api/diagnoses";
import { createPrescription } from "@/lib/api/prescriptions";
import type { DiagnosisSummaryResponse } from "@/types";

interface PrescriptionCreateFormProps {
  farmId: string;
}

// 처방 질문 입력(+선택: 진단 이력 연결) → 202 응답 즉시 상세 페이지로 이동해 폴링한다.
export default function PrescriptionCreateForm({ farmId }: PrescriptionCreateFormProps) {
  const router = useRouter();
  const [question, setQuestion] = useState("");
  const [diagnosisId, setDiagnosisId] = useState<string>("");
  const [diagnoses, setDiagnoses] = useState<DiagnosisSummaryResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
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
      setError(resolveErrorMessage(err));
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
      <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">처방 요청</h2>

      {diagnoses.length > 0 && (
        <div className="flex flex-col gap-1.5">
          <label htmlFor="diagnosis-select" className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
            관련 진단 이력 (선택)
          </label>
          <select
            id="diagnosis-select"
            value={diagnosisId}
            onChange={(e) => setDiagnosisId(e.target.value)}
            className="rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-900 outline-none focus:border-zinc-500 focus:ring-1 focus:ring-zinc-500 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50"
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
        <label htmlFor="question" className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
          질문
        </label>
        <textarea
          id="question"
          required
          maxLength={VALIDATION.prescriptionQuestion.maxLength}
          rows={4}
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          className="rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-900 outline-none focus:border-zinc-500 focus:ring-1 focus:ring-zinc-500 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50"
        />
      </div>

      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

      <button
        type="submit"
        disabled={submitting}
        className="self-start rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
      >
        {submitting ? "요청 중..." : "처방 요청"}
      </button>
    </form>
  );
}
