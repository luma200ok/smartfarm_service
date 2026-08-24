"use client";

import { useEffect, useState, type FormEvent } from "react";
import NutrientCalculationResult from "./NutrientCalculationResult";
import NutrientRecipeFormFields from "./NutrientRecipeFormFields";
import { useNutrientRecipeForm } from "./useNutrientRecipeForm";
import { Card, CardTitle } from "@/components/monitoring/ui";
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

  // 계산 결과가 화면의 현재 입력값과 다른 상태(stale)로 남지 않도록, 폼이 바뀌는 모든 경로에서
  // 기존 결과를 무효화한다(리뷰 픽스 #65 P2 — 저장 버튼이 stale 배합표와 함께 활성인 상태 금지).
  function invalidateCalculation() {
    setCalculation(null);
  }

  function handleStageChange(next: NutrientStage) {
    form.setStage(next);
    const preset = presets.find((p) => p.stage === next);
    if (preset) form.applyPresetTarget(preset.target);
    invalidateCalculation();
  }

  async function handleCalculate() {
    setCalcError(null);
    // 실패 시에도 이전 성공 결과가 남지 않도록 먼저 비운다(리뷰 픽스 #65 P2).
    invalidateCalculation();
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
    <Card className="flex flex-col gap-4 px-4 py-4">
      <div>
        <h2>
          <CardTitle size="lg">양액 배합 계산기</CardTitle>
        </h2>
        <p className="mt-1 text-xs text-dp-muted">작물: 토마토 (1차 범위 고정)</p>
      </div>

      {presetsError && <p className="text-sm text-dp-red-ink">{presetsError}</p>}

      <NutrientRecipeFormFields
        stage={form.stage}
        onStageChange={handleStageChange}
        target={form.target}
        onTargetChange={(t) => {
          form.setTarget(t);
          invalidateCalculation();
        }}
        tankVolumeL={form.tankVolumeL}
        onTankVolumeLChange={(v) => {
          form.setTankVolumeL(v);
          invalidateCalculation();
        }}
        concentrationFactor={form.concentrationFactor}
        onConcentrationFactorChange={(v) => {
          form.setConcentrationFactor(v);
          invalidateCalculation();
        }}
        sourceWaterCa={form.sourceWaterCa}
        sourceWaterMg={form.sourceWaterMg}
        sourceWaterEc={form.sourceWaterEc}
        onSourceWaterChange={(field, v) => {
          if (field === "ca") form.setSourceWaterCa(v);
          else if (field === "mg") form.setSourceWaterMg(v);
          else form.setSourceWaterEc(v);
          invalidateCalculation();
        }}
      />

      {calcError && <p className="text-sm text-dp-red-ink">{calcError}</p>}

      <button
        type="button"
        onClick={handleCalculate}
        disabled={calculating}
        className="self-start rounded-md bg-dp-ink px-4 py-2 text-sm font-medium text-dp-surface disabled:opacity-40"
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
          {saveError && <p className="text-sm text-dp-red-ink">{saveError}</p>}
          <div className="flex gap-2">
            <button
              type="submit"
              disabled={saving}
              className="rounded-md bg-dp-ink px-4 py-2 text-sm font-medium text-dp-surface disabled:opacity-40"
            >
              {saving ? "저장 중..." : "저장"}
            </button>
            <button
              type="button"
              onClick={() => setSaveOpen(false)}
              className="rounded-md border border-dp-line-strong px-4 py-2 text-sm text-dp-body"
            >
              취소
            </button>
          </div>
        </form>
      </Modal>
    </Card>
  );
}
