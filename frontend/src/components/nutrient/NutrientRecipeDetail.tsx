"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import NutrientCalculationResult from "./NutrientCalculationResult";
import NutrientRecipeFormFields, { type NutrientTargetFieldsState } from "./NutrientRecipeFormFields";
import FormField from "@/components/ui/FormField";
import { NUTRIENT_STAGE_LABELS } from "@/constants";
import { getMe } from "@/lib/api/auth";
import { isNotFound, resolveErrorMessage, resolveNutrientCalculationErrorMessage } from "@/lib/api/errorMessage";
import { getFarm } from "@/lib/api/farms";
import { deleteNutrientRecipe, getNutrientRecipe, updateNutrientRecipe } from "@/lib/api/nutrientRecipes";
import type { FarmResponse, NutrientRecipeRequest, NutrientRecipeResponse, NutrientStage } from "@/types";

interface NutrientRecipeDetailProps {
  farmId: string;
  recipeId: string;
}

function toNumber(v: string): number | null {
  if (v.trim() === "") return null;
  const n = Number(v);
  return Number.isNaN(n) ? null : n;
}

function toTargetFields(target: NutrientRecipeResponse["target"]): NutrientTargetFieldsState {
  return {
    n: String(target.n),
    p: String(target.p),
    k: String(target.k),
    ca: String(target.ca),
    mg: String(target.mg),
    s: String(target.s),
  };
}

// 저장된 양액 레시피 상세(이슈 #65) — 계산 결과 재표시(계산 스냅샷 그대로, 재계산 없음) +
// 작성자 본인이면 수정, 작성자 본인 또는 OWNER면 삭제. 버튼 노출은 보조 UX일 뿐이고
// 최종 권한 판정은 항상 서버 N002(FarmLogList·farmLogs.ts와 동일한 "서버가 최종 판정" 관례).
export default function NutrientRecipeDetail({ farmId, recipeId }: NutrientRecipeDetailProps) {
  const router = useRouter();
  const [recipe, setRecipe] = useState<NutrientRecipeResponse | null>(null);
  const [farm, setFarm] = useState<FarmResponse | null>(null);
  const [myUserId, setMyUserId] = useState<number | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

  const [editing, setEditing] = useState(false);
  const [name, setName] = useState("");
  const [stage, setStage] = useState<NutrientStage>("SEEDLING");
  const [target, setTarget] = useState<NutrientTargetFieldsState>({ n: "", p: "", k: "", ca: "", mg: "", s: "" });
  const [tankVolumeL, setTankVolumeL] = useState("");
  const [concentrationFactor, setConcentrationFactor] = useState("");
  const [sourceWaterCa, setSourceWaterCa] = useState("");
  const [sourceWaterMg, setSourceWaterMg] = useState("");
  const [sourceWaterEc, setSourceWaterEc] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  function applyRecipe(data: NutrientRecipeResponse) {
    setRecipe(data);
    setName(data.name);
    setStage(data.stage);
    setTarget(toTargetFields(data.target));
    setTankVolumeL(String(data.tankVolumeL));
    setConcentrationFactor(String(data.concentrationFactor));
    setSourceWaterCa(data.sourceWater?.ca != null ? String(data.sourceWater.ca) : "");
    setSourceWaterMg(data.sourceWater?.mg != null ? String(data.sourceWater.mg) : "");
    setSourceWaterEc(data.sourceWater?.ec != null ? String(data.sourceWater.ec) : "");
  }

  useEffect(() => {
    let cancelled = false;
    getNutrientRecipe(farmId, recipeId)
      .then((data) => {
        if (!cancelled) applyRecipe(data);
      })
      .catch((err) => {
        if (cancelled) return;
        if (isNotFound(err)) setNotFound(true);
        else setLoadError(resolveErrorMessage(err));
      });
    getFarm(farmId)
      .then((f) => {
        if (!cancelled) setFarm(f);
      })
      .catch(() => {
        // 수정/삭제 버튼 노출 판정 보조용일 뿐 — 실패해도 조용히 넘어간다(FarmLogList와 동일).
      });
    getMe()
      .then((me) => {
        if (!cancelled) setMyUserId(me.id);
      })
      .catch(() => {
        // myUserId=null 유지 — 본인 식별이 필요한 수정 버튼만 숨겨진다.
      });
    return () => {
      cancelled = true;
    };
  }, [farmId, recipeId]);

  if (notFound) {
    return (
      <p className="px-6 py-6 text-sm text-zinc-500 dark:text-zinc-400">
        양액 레시피를 찾을 수 없습니다.{" "}
        <Link href={`/farms/${farmId}/nutrient`} className="underline">
          목록으로
        </Link>
      </p>
    );
  }

  if (loadError) {
    return <p className="px-6 py-6 text-sm text-red-600 dark:text-red-400">{loadError}</p>;
  }

  if (!recipe) {
    return <p className="px-6 py-6 text-sm text-zinc-500 dark:text-zinc-400">불러오는 중...</p>;
  }

  const isAuthor = myUserId !== null && recipe.createdBy === myUserId;
  const isOwner = farm?.myRole === "OWNER";
  const canEdit = isAuthor;
  const canDelete = isAuthor || isOwner;

  async function handleSave(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setSaveError(null);

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
      setSaveError("모든 목표 농도·탱크 용량·농축배율을 입력해주세요.");
      return;
    }

    const swCa = toNumber(sourceWaterCa);
    const swMg = toNumber(sourceWaterMg);
    const swEc = toNumber(sourceWaterEc);
    const hasSourceWater = swCa !== null || swMg !== null || swEc !== null;

    const payload: NutrientRecipeRequest = {
      name,
      stage,
      target: { n, p, k, ca, mg, s },
      tankVolumeL: tank,
      concentrationFactor: factor,
      sourceWater: hasSourceWater
        ? { ca: swCa ?? undefined, mg: swMg ?? undefined, ec: swEc ?? undefined }
        : undefined,
    };

    setSaving(true);
    try {
      const updated = await updateNutrientRecipe(farmId, recipeId, payload);
      applyRecipe(updated);
      setEditing(false);
    } catch (err) {
      setSaveError(resolveNutrientCalculationErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!window.confirm("이 레시피를 삭제하시겠습니까?")) return;
    setDeleteError(null);
    setDeleting(true);
    try {
      await deleteNutrientRecipe(farmId, recipeId);
      router.push(`/farms/${farmId}/nutrient`);
    } catch (err) {
      setDeleteError(resolveErrorMessage(err));
      setDeleting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6 px-6 py-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">{recipe.name}</h2>
          <span className="rounded bg-zinc-100 px-2 py-0.5 text-xs text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">
            {NUTRIENT_STAGE_LABELS[recipe.stage] ?? recipe.stage}
          </span>
        </div>
        {(canEdit || canDelete) && !editing && (
          <div className="flex gap-2 text-sm">
            {canEdit && (
              <button
                type="button"
                onClick={() => setEditing(true)}
                className="text-zinc-500 hover:underline dark:text-zinc-400"
              >
                수정
              </button>
            )}
            {canDelete && (
              <button
                type="button"
                disabled={deleting}
                onClick={handleDelete}
                className="text-red-600 hover:underline disabled:opacity-60 dark:text-red-400"
              >
                삭제
              </button>
            )}
          </div>
        )}
      </div>

      {deleteError && <p className="text-sm text-red-600 dark:text-red-400">{deleteError}</p>}

      <p className="text-xs text-zinc-500 dark:text-zinc-400">
        탱크 {recipe.tankVolumeL}L · 농축배율 {recipe.concentrationFactor}배
        {recipe.sourceWater &&
          (recipe.sourceWater.ca != null || recipe.sourceWater.mg != null || recipe.sourceWater.ec != null) && (
            <> · 원수 보정 적용됨</>
          )}
      </p>

      {editing ? (
        <form
          onSubmit={handleSave}
          className="flex flex-col gap-4 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800"
        >
          <FormField
            id="nutrient-edit-name"
            label="레시피 이름"
            required
            maxLength={50}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <NutrientRecipeFormFields
            stage={stage}
            onStageChange={setStage}
            target={target}
            onTargetChange={setTarget}
            tankVolumeL={tankVolumeL}
            onTankVolumeLChange={setTankVolumeL}
            concentrationFactor={concentrationFactor}
            onConcentrationFactorChange={setConcentrationFactor}
            sourceWaterCa={sourceWaterCa}
            sourceWaterMg={sourceWaterMg}
            sourceWaterEc={sourceWaterEc}
            onSourceWaterChange={(field, v) => {
              if (field === "ca") setSourceWaterCa(v);
              else if (field === "mg") setSourceWaterMg(v);
              else setSourceWaterEc(v);
            }}
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
              onClick={() => {
                setEditing(false);
                applyRecipe(recipe);
              }}
              className="rounded-md border border-zinc-300 px-4 py-2 text-sm dark:border-zinc-700"
            >
              취소
            </button>
          </div>
        </form>
      ) : (
        <NutrientCalculationResult calculation={recipe.calculation} />
      )}
    </div>
  );
}
