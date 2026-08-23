"use client";

import { useState, type FormEvent } from "react";
import FormField from "@/components/ui/FormField";
import { FARM_LOG_TYPE_LABELS, VALIDATION } from "@/constants";
import type { FarmLogRequest, FarmLogType } from "@/types";

const TYPE_OPTIONS: FarmLogType[] = ["WATERING", "FERTILIZING", "PRUNING", "HARVEST", "PEST_CONTROL", "ETC"];

function todayLocalDate(): string {
  // toISOString()은 UTC 기준이라 서버 로컬(Asia/Seoul) 자정 부근에 날짜가 하루 밀릴 수 있어
  // 로컬 타임존 기준으로 직접 조립한다.
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

interface FarmLogFormProps {
  initial?: FarmLogRequest;
  submitting: boolean;
  error: string | null;
  submitLabel: string;
  onSubmit: (payload: FarmLogRequest) => void;
  onCancel: () => void;
}

// 작업일지 작성/수정 공용 폼(이슈 #57) — Modal 내부에서 create/edit 양쪽에 재사용.
export default function FarmLogForm({
  initial,
  submitting,
  error,
  submitLabel,
  onSubmit,
  onCancel,
}: FarmLogFormProps) {
  const [logDate, setLogDate] = useState(initial?.logDate ?? todayLocalDate());
  const [type, setType] = useState<FarmLogType>(initial?.type ?? "WATERING");
  const [memo, setMemo] = useState(initial?.memo ?? "");

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    onSubmit({ logDate, type, memo: memo || undefined });
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <FormField
        id="log-date"
        label="작업일"
        type="date"
        required
        value={logDate}
        onChange={(e) => setLogDate(e.target.value)}
      />

      <div className="flex flex-col gap-1.5">
        <label htmlFor="log-type" className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
          작업 종류
        </label>
        <select
          id="log-type"
          value={type}
          onChange={(e) => setType(e.target.value as FarmLogType)}
          className="rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-900 outline-none focus:border-zinc-500 focus:ring-1 focus:ring-zinc-500 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50"
        >
          {TYPE_OPTIONS.map((opt) => (
            <option key={opt} value={opt}>
              {FARM_LOG_TYPE_LABELS[opt]}
            </option>
          ))}
        </select>
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="log-memo" className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
          메모 (선택)
        </label>
        <textarea
          id="log-memo"
          rows={4}
          maxLength={VALIDATION.farmLogMemo.maxLength}
          value={memo}
          onChange={(e) => setMemo(e.target.value)}
          className="rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-900 outline-none focus:border-zinc-500 focus:ring-1 focus:ring-zinc-500 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50"
        />
      </div>

      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

      <div className="flex gap-2">
        <button
          type="submit"
          disabled={submitting}
          className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
        >
          {submitting ? "저장 중..." : submitLabel}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="rounded-md border border-zinc-300 px-4 py-2 text-sm dark:border-zinc-700"
        >
          취소
        </button>
      </div>
    </form>
  );
}
