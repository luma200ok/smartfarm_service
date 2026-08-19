"use client";

import { useState, type FormEvent } from "react";
import DiagnosisResultCard from "./DiagnosisResultCard";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { createDiagnosis } from "@/lib/api/diagnoses";
import type { DiagnosisResponse } from "@/types";

interface DiagnosisUploadFormProps {
  farmId: string;
  onUploaded?: () => void;
}

// 진단 이미지 업로드(multipart) → 결과 렌더. contract §3 POST /diagnoses(동기).
export default function DiagnosisUploadForm({ farmId, onUploaded }: DiagnosisUploadFormProps) {
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<DiagnosisResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

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
      <form onSubmit={handleSubmit} className="flex flex-col gap-3 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
        <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">작물 이미지 진단</h2>
        <input
          type="file"
          accept="image/*"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          className="text-sm text-zinc-700 dark:text-zinc-300"
        />
        {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
        <button
          type="submit"
          disabled={submitting || !file}
          className="self-start rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
        >
          {submitting ? "진단 중..." : "진단하기"}
        </button>
      </form>
      {result && <DiagnosisResultCard diagnosis={result} />}
    </div>
  );
}
