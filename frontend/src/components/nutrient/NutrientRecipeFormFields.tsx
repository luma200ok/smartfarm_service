"use client";

import FormField from "@/components/ui/FormField";
import { NUTRIENT_STAGE_LABELS } from "@/constants";
import type { NutrientStage } from "@/types";

export interface NutrientTargetFieldsState {
  n: string;
  p: string;
  k: string;
  ca: string;
  mg: string;
  s: string;
}

type SourceWaterField = "ca" | "mg" | "ec";

interface NutrientRecipeFormFieldsProps {
  stage: NutrientStage;
  onStageChange: (stage: NutrientStage) => void;
  target: NutrientTargetFieldsState;
  onTargetChange: (target: NutrientTargetFieldsState) => void;
  tankVolumeL: string;
  onTankVolumeLChange: (value: string) => void;
  concentrationFactor: string;
  onConcentrationFactorChange: (value: string) => void;
  sourceWaterCa: string;
  sourceWaterMg: string;
  sourceWaterEc: string;
  onSourceWaterChange: (field: SourceWaterField, value: string) => void;
}

const STAGE_OPTIONS: NutrientStage[] = ["SEEDLING", "VEGETATIVE", "FRUITING", "HARVEST"];

const TARGET_FIELD_LABELS: Record<keyof NutrientTargetFieldsState, string> = {
  n: "질소(N)",
  p: "인(P)",
  k: "칼륨(K)",
  ca: "칼슘(Ca)",
  mg: "마그네슘(Mg)",
  s: "황(S)",
};

const TARGET_FIELD_KEYS = Object.keys(TARGET_FIELD_LABELS) as (keyof NutrientTargetFieldsState)[];

// NutrientCalculator(계산기)·NutrientRecipeDetail(수정)이 공유하는 순수 입력 필드 묶음
// (contract §4.9). 값·검증·제출은 부모가 소유하고 이 컴포넌트는 렌더만 담당한다.
// 목표 ppm·탱크 용량 등 range는 서버(Bean Validation)가 최종 판정하므로
// 여기서는 HTML5 min/max 정도의 가벼운 가드만 둔다(클라 검증 과설계 금지).
export default function NutrientRecipeFormFields({
  stage,
  onStageChange,
  target,
  onTargetChange,
  tankVolumeL,
  onTankVolumeLChange,
  concentrationFactor,
  onConcentrationFactorChange,
  sourceWaterCa,
  sourceWaterMg,
  sourceWaterEc,
  onSourceWaterChange,
}: NutrientRecipeFormFieldsProps) {
  function handleTargetFieldChange(key: keyof NutrientTargetFieldsState, value: string) {
    onTargetChange({ ...target, [key]: value });
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <label htmlFor="nutrient-stage" className="text-sm font-medium text-dp-body">
          생육 단계
        </label>
        <select
          id="nutrient-stage"
          value={stage}
          onChange={(e) => onStageChange(e.target.value as NutrientStage)}
          className="rounded-md border border-dp-line-strong bg-dp-surface px-3 py-2 text-sm text-dp-ink"
        >
          {STAGE_OPTIONS.map((opt) => (
            <option key={opt} value={opt}>
              {NUTRIENT_STAGE_LABELS[opt]}
            </option>
          ))}
        </select>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {TARGET_FIELD_KEYS.map((key) => (
          <FormField
            key={key}
            id={`nutrient-target-${key}`}
            label={`${TARGET_FIELD_LABELS[key]} (ppm)`}
            type="number"
            step="0.1"
            min={0}
            max={1000}
            required
            value={target[key]}
            onChange={(e) => handleTargetFieldChange(key, e.target.value)}
          />
        ))}
      </div>

      <div className="grid grid-cols-2 gap-3">
        <FormField
          id="nutrient-tank-volume"
          label="탱크 용량(L)"
          type="number"
          step="1"
          min={1}
          max={10000}
          required
          value={tankVolumeL}
          onChange={(e) => onTankVolumeLChange(e.target.value)}
        />
        <FormField
          id="nutrient-concentration-factor"
          label="농축배율"
          type="number"
          step="1"
          min={1}
          max={500}
          required
          value={concentrationFactor}
          onChange={(e) => onConcentrationFactorChange(e.target.value)}
        />
      </div>

      <details className="rounded-md border border-dp-line p-3 text-sm">
        <summary className="cursor-pointer font-medium text-dp-body">
          원수 분석값 (선택 — 있으면 Ca/Mg 목표치에서 차감 보정)
        </summary>
        <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-3">
          <FormField
            id="nutrient-source-water-ca"
            label="원수 Ca(ppm)"
            type="number"
            step="0.1"
            min={0}
            value={sourceWaterCa}
            onChange={(e) => onSourceWaterChange("ca", e.target.value)}
          />
          <FormField
            id="nutrient-source-water-mg"
            label="원수 Mg(ppm)"
            type="number"
            step="0.1"
            min={0}
            value={sourceWaterMg}
            onChange={(e) => onSourceWaterChange("mg", e.target.value)}
          />
          <FormField
            id="nutrient-source-water-ec"
            label="원수 EC(dS/m)"
            type="number"
            step="0.01"
            min={0}
            value={sourceWaterEc}
            onChange={(e) => onSourceWaterChange("ec", e.target.value)}
          />
        </div>
      </details>
    </div>
  );
}
