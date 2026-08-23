"use client";

import { useEffect, useState, type FormEvent } from "react";
import NutrientCalculationResult from "./NutrientCalculationResult";
import NutrientRecipeFormFields from "./NutrientRecipeFormFields";
import { useNutrientRecipeForm } from "./useNutrientRecipeForm";
import FormField from "@/components/ui/FormField";
import Modal from "@/components/ui/Modal";
import { resolveErrorMessage, resolveNutrientCalculationErrorMessage } from "@/lib/api/errorMessage";
import { listNutrientPresets } from "@/lib/api/nutrientPresets";
import { calculateNutrientRecipe, createNutrientRecipe } from "@/lib/api/nutrientRecipes";
import type { NutrientCalculationResponse, NutrientPresetResponse, NutrientRecipeResponse, NutrientStage } from "@/types";

interface NutrientCalculatorProps {
  farmId: string;
  onSaved: (recipe: NutrientRecipeResponse) => void;
}

// 양액 배합 계산기(이슈 #64·#65) — 프리셋(cropType=TOMATO 고정) 선택 → 목표 ppm 자동 채움
// (직접 수정 가능) → 계산(저장 없는 미리보기) → 결과 확인 후 이름 붙여 레시피로 저장.
// 계산은 전부 서버(NutrientCalculationEngine)가 수행 — 이 컴포넌트는 요청 조립과 결과 표시만 한다.
// 폼 필드 상태·요청 조립은 useNutrientRecipeForm(NutrientRecipeDetail과 공유, 리뷰 픽스 #65 P2-1).
export default function NutrientCalculator({ farmId, onSaved }: NutrientCalculatorProps) {
  const [presets, setPresets] = useState<NutrientPresetResponse[]>([]);
  const [presetsError, setPresetsError] = useState<string | null>(null);

  const form = useNutrientRecipeForm({ tankVolumeL: "1000", concentrationFactor: "100" });

  const [calculating, setCalculating] = useState(false);
  const [calcError, setCalcError] = useState<string | null>(null);
  const [calculation, setCalculation] = useState<NutrientCalculationResponse | null>(null);

  const [saveOpen, setSaveOpen] = useState(false);
  const [name, setName] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  // 프리셋은 최초 1회만 조회(cropType=TOMATO 고정) — 4단계 전부 응답되므로 스테이지 변경 시
  // 재조회 없이 로컬에서 매칭한다.
  useEffect(() => {
    let cancelled = false;
    listNutrientPresets("TOMATO")
      .then((res) => {
        if (cancelled) return;
        setPresets(res);
        const initial = res.find((p) => p.stage === "SEEDLING");
        if (initial) form.applyPresetTarget(initial.target);
      })
      .catch((err) => {
        if (!cancelled) setPresetsError(resolveErrorMessage(err));
      });
    return () => {
      cancelled = true;
    };
    // form은 useNutrientRecipeForm이 매 렌더 새 객체를 반환하지만 최초 1회 조회 목적상
    // 마운트 시점 함수만 있으면 충분하다(deps에 넣으면 매 렌더 재조회됨).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function handleStageChange(next: NutrientStage) {
    form.setStage(next);
    const preset = presets.find((p) => p.stage === next);
    if (preset) form.applyPresetTarget(preset.target);
  }

  async function handleCalculate() {
    setCalcError(null);
    const request = form.buildRequest();
    if (!request) {
      setCalcError("모든 목표 농도·탱크 용량·농축배율을 입력해주세요.");
      return;
    }
    setCalculating(true);
    try {
      const result = await calculateNutrientRecipe(farmId, request);
      setCalculation(result);
    } catch (err) {
      setCalcError(resolveNutrientCalculationErrorMessage(err));
    } finally {
      setCalculating(false);
    }
  }

  async function handleSave(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setSaveError(null);
    const request = form.buildSaveRequest(name);
    if (!request) {
      setSaveError("계산에 사용한 값이 유효하지 않습니다. 계산을 다시 실행해주세요.");
      return;
    }
    setSaving(true);
    try {
      const recipe = await createNutrientRecipe(farmId, request);
      setSaveOpen(false);
      setName("");
      onSaved(recipe);
    } catch (err) {
      setSaveError(resolveNutrientCalculationErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="flex flex-col gap-4 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
      <div>
        <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">양액 배합 계산기</h2>
        <p className="text-xs text-zinc-500 dark:text-zinc-400">작물: 토마토 (1차 범위 고정)</p>
      </div>

      {presetsError && <p className="text-sm text-red-600 dark:text-red-400">{presetsError}</p>}

      <NutrientRecipeFormFields
        stage={form.stage}
        onStageChange={handleStageChange}
        target={form.target}
        onTargetChange={form.setTarget}
        tankVolumeL={form.tankVolumeL}
        onTankVolumeLChange={form.setTankVolumeL}
        concentrationFactor={form.concentrationFactor}
        onConcentrationFactorChange={form.setConcentrationFactor}
        sourceWaterCa={form.sourceWaterCa}
        sourceWaterMg={form.sourceWaterMg}
        sourceWaterEc={form.sourceWaterEc}
        onSourceWaterChange={(field, v) => {
          if (field === "ca") form.setSourceWaterCa(v);
          else if (field === "mg") form.setSourceWaterMg(v);
          else form.setSourceWaterEc(v);
        }}
      />

      {calcError && <p className="text-sm text-red-600 dark:text-red-400">{calcError}</p>}

      <button
        type="button"
        onClick={handleCalculate}
        disabled={calculating}
        className="self-start rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
      >
        {calculating ? "계산 중..." : "계산"}
      </button>

      {calculation && <NutrientCalculationResult calculation={calculation} onSaveClick={() => setSaveOpen(true)} />}

      <Modal open={saveOpen} onClose={() => setSaveOpen(false)} title="레시피로 저장">
        <form onSubmit={handleSave} className="flex flex-col gap-4">
          <FormField
            id="nutrient-recipe-name"
            label="레시피 이름"
            required
            maxLength={50}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          {saveError && <p className="text-sm text-red-600 dark:text-red-400">{saveError}</p>}
          <div className="flex gap-2">
            <button
              type="submit"
              disabled={saving}
              className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
            >
              {saving ? "저장 중..." : "저장"}
            </button>
            <button
              type="button"
              onClick={() => setSaveOpen(false)}
              className="rounded-md border border-zinc-300 px-4 py-2 text-sm dark:border-zinc-700"
            >
              취소
            </button>
          </div>
        </form>
      </Modal>
    </section>
  );
}
