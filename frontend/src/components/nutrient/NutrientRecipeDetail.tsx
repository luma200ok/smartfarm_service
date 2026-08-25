"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import NutrientCalculationResult from "./NutrientCalculationResult";
import NutrientRecipeFormFields, { type NutrientTargetFieldsState } from "./NutrientRecipeFormFields";
import { useNutrientRecipeForm } from "./useNutrientRecipeForm";
import { StatusBadge } from "@/components/monitoring/ui";
import FormField from "@/components/ui/FormField";
import { NUTRIENT_STAGE_LABELS } from "@/constants";
import { getMe } from "@/lib/api/auth";
import { isNotFound, resolveErrorMessage, resolveNutrientCalculationErrorMessage } from "@/lib/api/errorMessage";
import { getFarm } from "@/lib/api/farms";
import { deleteNutrientRecipe, getNutrientRecipe, updateNutrientRecipe } from "@/lib/api/nutrientRecipes";
import { hasFarmRoleAtLeast } from "@/lib/roles";
import type { FarmResponse, NutrientRecipeResponse } from "@/types";

interface NutrientRecipeDetailProps {
  farmId: string;
  recipeId: string;
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
// 작성자 본인이면 수정, 작성자 본인 또는 ADMIN이면 삭제. 버튼 노출은 보조 UX일 뿐이고
// 최종 권한 판정은 항상 서버 N002(FarmLogList·farmLogs.ts와 동일한 "서버가 최종 판정" 관례).
// 폼 필드 상태·요청 조립은 useNutrientRecipeForm(NutrientCalculator와 공유, 리뷰 픽스 #65 P2-1).
export default function NutrientRecipeDetail({ farmId, recipeId }: NutrientRecipeDetailProps) {
  const router = useRouter();
  const [recipe, setRecipe] = useState<NutrientRecipeResponse | null>(null);
  const [farm, setFarm] = useState<FarmResponse | null>(null);
  const [myUserId, setMyUserId] = useState<number | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

  const [editing, setEditing] = useState(false);
  const [name, setName] = useState("");
  const form = useNutrientRecipeForm();
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  function applyRecipe(data: NutrientRecipeResponse) {
    setRecipe(data);
    setName(data.name);
    form.reset({
      stage: data.stage,
      target: toTargetFields(data.target),
      tankVolumeL: String(data.tankVolumeL),
      concentrationFactor: String(data.concentrationFactor),
      sourceWaterCa: data.sourceWater?.ca != null ? String(data.sourceWater.ca) : "",
      sourceWaterMg: data.sourceWater?.mg != null ? String(data.sourceWater.mg) : "",
      sourceWaterEc: data.sourceWater?.ec != null ? String(data.sourceWater.ec) : "",
    });
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [farmId, recipeId]);

  if (notFound) {
    return (
      <p className="px-6 py-6 text-sm text-dp-muted">
        양액 레시피를 찾을 수 없습니다.{" "}
        <Link href={`/farms/${farmId}/nutrient`} className="underline">
          목록으로
        </Link>
      </p>
    );
  }

  if (loadError) {
    return <p className="px-6 py-6 text-sm text-dp-red-ink">{loadError}</p>;
  }

  if (!recipe) {
    return <p className="px-6 py-6 text-sm text-dp-muted">불러오는 중...</p>;
  }

  const isAuthor = myUserId !== null && recipe.createdBy === myUserId;
  // 삭제=작성자 본인 또는 ADMIN(구 OWNER 승계, contract §2·N002) — 버튼 노출은 보조 UX일 뿐.
  const isAdmin = hasFarmRoleAtLeast(farm?.myRole, "ADMIN");
  const canEdit = isAuthor;
  const canDelete = isAuthor || isAdmin;

  async function handleSave(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setSaveError(null);

    const payload = form.buildSaveRequest(name);
    if (!payload) {
      setSaveError("모든 목표 농도·탱크 용량·농축배율을 입력해주세요.");
      return;
    }

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
          <h2 className="text-lg font-semibold text-dp-ink">{recipe.name}</h2>
          <StatusBadge label={NUTRIENT_STAGE_LABELS[recipe.stage] ?? recipe.stage} tone="neutral" />
        </div>
        {(canEdit || canDelete) && !editing && (
          <div className="flex gap-2 text-sm">
            {canEdit && (
              <button
                type="button"
                onClick={() => setEditing(true)}
                className="text-dp-sub hover:text-dp-ink hover:underline"
              >
                수정
              </button>
            )}
            {canDelete && (
              <button
                type="button"
                disabled={deleting}
                onClick={handleDelete}
                className="text-dp-red-ink hover:underline disabled:opacity-60"
              >
                삭제
              </button>
            )}
          </div>
        )}
      </div>

      {deleteError && <p className="text-sm text-dp-red-ink">{deleteError}</p>}

      <p className="text-xs text-dp-muted">
        탱크 {recipe.tankVolumeL}L · 농축배율 {recipe.concentrationFactor}배
        {recipe.sourceWater &&
          (recipe.sourceWater.ca != null || recipe.sourceWater.mg != null || recipe.sourceWater.ec != null) && (
            <> · 원수 보정 적용됨</>
          )}
      </p>

      {editing ? (
        <form onSubmit={handleSave} className="flex flex-col gap-4 rounded-[10px] border border-dp-line bg-dp-surface p-4">
          <FormField
            id="nutrient-edit-name"
            label="레시피 이름"
            required
            maxLength={50}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <NutrientRecipeFormFields
            stage={form.stage}
            onStageChange={form.setStage}
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
              onClick={() => {
                setEditing(false);
                applyRecipe(recipe);
              }}
              className="rounded-md border border-dp-line-strong px-4 py-2 text-sm text-dp-body"
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
