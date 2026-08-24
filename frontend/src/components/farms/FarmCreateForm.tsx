"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Card, CardTitle } from "@/components/monitoring/ui";
import FormField from "@/components/ui/FormField";
import { VALIDATION } from "@/constants";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { createFarm } from "@/lib/api/farms";
import { notifyFarmsChanged } from "@/lib/farmsBus";
import type { CropType } from "@/types";

// 1차 범위: cropType은 TOMATO만(ai-server 모델 전용, contract §4). 확장 대비 select 유지.
const CROP_OPTIONS: { value: CropType; label: string }[] = [{ value: "TOMATO", label: "토마토" }];

interface FarmCreateFormProps {
  // "card": 단독 페이지 등에서 카드 테두리+제목 포함(기존 동작, 기본값).
  // "plain": 모달 내부처럼 바깥에서 이미 카드/제목을 그리는 컨텍스트에서 중복 제거.
  variant?: "card" | "plain";
}

// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card·CardTitle)로 통일한다(이슈 #109).
export default function FarmCreateForm({ variant = "card" }: FarmCreateFormProps) {
  const router = useRouter();
  const [name, setName] = useState("");
  const [cropType, setCropType] = useState<CropType>("TOMATO");
  const [location, setLocation] = useState("");
  const [nameError, setNameError] = useState<string | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);

    if (name.length < VALIDATION.farmName.minLength || name.length > VALIDATION.farmName.maxLength) {
      setNameError(
        `농장명은 ${VALIDATION.farmName.minLength}~${VALIDATION.farmName.maxLength}자여야 합니다.`
      );
      return;
    }
    setNameError(undefined);

    setSubmitting(true);
    try {
      const farm = await createFarm({ name, cropType, location: location || undefined });
      notifyFarmsChanged(); // Sidebar(#42) 농장 리스트 재조회
      router.push(`/farms/${farm.id}`);
      router.refresh();
    } catch (err) {
      setError(resolveErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  const form = (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      {variant === "card" && <CardTitle as="h2">새 농장 만들기</CardTitle>}

      <FormField
        id="farm-name"
        label="농장명"
        type="text"
        required
        minLength={VALIDATION.farmName.minLength}
        maxLength={VALIDATION.farmName.maxLength}
        value={name}
        onChange={(e) => setName(e.target.value)}
        error={nameError}
      />

      <div className="flex flex-col gap-1.5">
        <label htmlFor="farm-crop" className="text-sm font-medium text-dp-body">
          작물
        </label>
        <select
          id="farm-crop"
          value={cropType}
          onChange={(e) => setCropType(e.target.value as CropType)}
          className="rounded-md border border-dp-line-strong bg-dp-surface px-3 py-2 text-sm text-dp-ink outline-none focus:border-dp-line-strong focus:ring-1 focus:ring-dp-line-strong"
        >
          {CROP_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      </div>

      <FormField
        id="farm-location"
        label="위치 (선택)"
        type="text"
        value={location}
        onChange={(e) => setLocation(e.target.value)}
      />

      {error && <p className="text-sm text-dp-red-ink">{error}</p>}

      <button
        type="submit"
        disabled={submitting}
        className="self-start rounded-md bg-dp-ink px-4 py-2 text-sm font-medium text-dp-surface transition-colors hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
      >
        {submitting ? "생성 중..." : "농장 생성"}
      </button>
    </form>
  );

  if (variant === "plain") return form;

  return <Card className="p-4">{form}</Card>;
}
