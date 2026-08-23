"use client";

import { useState } from "react";
import type { NutrientRecipeRequest, NutrientRecipeSaveRequest, NutrientStage, NutrientTargetResponse } from "@/types";
import type { NutrientTargetFieldsState } from "./NutrientRecipeFormFields";

export interface NutrientRecipeFormInit {
  stage?: NutrientStage;
  target?: NutrientTargetFieldsState;
  tankVolumeL?: string;
  concentrationFactor?: string;
  sourceWaterCa?: string;
  sourceWaterMg?: string;
  sourceWaterEc?: string;
}

const EMPTY_TARGET: NutrientTargetFieldsState = { n: "", p: "", k: "", ca: "", mg: "", s: "" };

function toNumber(v: string): number | null {
  if (v.trim() === "") return null;
  const n = Number(v);
  return Number.isNaN(n) ? null : n;
}

function targetFieldsFromResponse(target: NutrientTargetResponse): NutrientTargetFieldsState {
  return {
    n: String(target.n),
    p: String(target.p),
    k: String(target.k),
    ca: String(target.ca),
    mg: String(target.mg),
    s: String(target.s),
  };
}

/**
 * 양액 배합 폼(stage/target/tankVolumeL/concentrationFactor/sourceWater) 상태와 검증 로직을
 * NutrientCalculator(계산기)·NutrientRecipeDetail(수정)이 공유하기 위한 훅(리뷰 픽스 #65 P2-1 —
 * 두 컴포넌트에 거의 동일하게 중복돼 있던 toNumber/필수값 체크를 여기로 추출했다. 순수 리팩터링 —
 * 동작 변화 없음: 계산은 target 6종·탱크용량·농축배율 전부 필수, 저장은 그 위에 name까지 필수).
 * API 호출·모달·제출 흐름(무엇을 언제 부를지)은 각 컴포넌트가 그대로 소유한다 — 이 훅은
 * 필드 상태 + 요청 조립만 담당.
 */
export function useNutrientRecipeForm(init?: NutrientRecipeFormInit) {
  const [stage, setStage] = useState<NutrientStage>(init?.stage ?? "SEEDLING");
  const [target, setTarget] = useState<NutrientTargetFieldsState>(init?.target ?? EMPTY_TARGET);
  const [tankVolumeL, setTankVolumeL] = useState(init?.tankVolumeL ?? "");
  const [concentrationFactor, setConcentrationFactor] = useState(init?.concentrationFactor ?? "");
  const [sourceWaterCa, setSourceWaterCa] = useState(init?.sourceWaterCa ?? "");
  const [sourceWaterMg, setSourceWaterMg] = useState(init?.sourceWaterMg ?? "");
  const [sourceWaterEc, setSourceWaterEc] = useState(init?.sourceWaterEc ?? "");

  // 프리셋 선택(계산기) 시 target 6종을 프리셋값으로 채운다 — 사용자가 이후 직접 수정 가능.
  function applyPresetTarget(presetTarget: NutrientTargetResponse) {
    setTarget(targetFieldsFromResponse(presetTarget));
  }

  // 저장된 레시피 로드(상세/수정 진입) 시 폼 전체를 그 값으로 되돌린다.
  function reset(next: NutrientRecipeFormInit) {
    setStage(next.stage ?? "SEEDLING");
    setTarget(next.target ?? EMPTY_TARGET);
    setTankVolumeL(next.tankVolumeL ?? "");
    setConcentrationFactor(next.concentrationFactor ?? "");
    setSourceWaterCa(next.sourceWaterCa ?? "");
    setSourceWaterMg(next.sourceWaterMg ?? "");
    setSourceWaterEc(next.sourceWaterEc ?? "");
  }

  // 계산(미리보기) 요청 조립 — name 없음. target 6종·탱크용량·농축배율 중 하나라도 비었으면 null.
  function buildRequest(): NutrientRecipeRequest | null {
    const n = toNumber(target.n);
    const p = toNumber(target.p);
    const k = toNumber(target.k);
    const ca = toNumber(target.ca);
    const mg = toNumber(target.mg);
    const s = toNumber(target.s);
    const tank = toNumber(tankVolumeL);
    const factor = toNumber(concentrationFactor);
    if (
      n === null ||
      p === null ||
      k === null ||
      ca === null ||
      mg === null ||
      s === null ||
      tank === null ||
      factor === null
    ) {
      return null;
    }

    const swCa = toNumber(sourceWaterCa);
    const swMg = toNumber(sourceWaterMg);
    const swEc = toNumber(sourceWaterEc);
    const hasSourceWater = swCa !== null || swMg !== null || swEc !== null;

    return {
      stage,
      target: { n, p, k, ca, mg, s },
      tankVolumeL: tank,
      concentrationFactor: factor,
      sourceWater: hasSourceWater
        ? { ca: swCa ?? undefined, mg: swMg ?? undefined, ec: swEc ?? undefined }
        : undefined,
    };
  }

  // 저장(생성/수정) 요청 조립 — buildRequest 위에 name을 필수로 얹는다(타입 레벨 강제, P2-2).
  function buildSaveRequest(name: string): NutrientRecipeSaveRequest | null {
    const base = buildRequest();
    if (!base) return null;
    return { ...base, name };
  }

  return {
    stage,
    setStage,
    target,
    setTarget,
    tankVolumeL,
    setTankVolumeL,
    concentrationFactor,
    setConcentrationFactor,
    sourceWaterCa,
    setSourceWaterCa,
    sourceWaterMg,
    setSourceWaterMg,
    sourceWaterEc,
    setSourceWaterEc,
    applyPresetTarget,
    reset,
    buildRequest,
    buildSaveRequest,
  };
}
