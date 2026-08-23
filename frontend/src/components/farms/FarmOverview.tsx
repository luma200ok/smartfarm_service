"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import EnvironmentHistoryChart from "@/components/environment/EnvironmentHistoryChart";
import EnvThresholdForm from "@/components/environment/EnvThresholdForm";
import EnvironmentWidget from "@/components/environment/EnvironmentWidget";
import ForecastWidget from "@/components/environment/ForecastWidget";
import VpdWidget from "@/components/environment/VpdWidget";
import FormField from "@/components/ui/FormField";
import { VALIDATION } from "@/constants";
import { resolveErrorMessage, isNotFound } from "@/lib/api/errorMessage";
import { deleteFarm, getFarm, updateFarm } from "@/lib/api/farms";
import { notifyFarmsChanged } from "@/lib/farmsBus";
import type { FarmResponse } from "@/types";

const CROP_LABELS: Record<string, string> = { TOMATO: "토마토" };
const ROLE_LABELS: Record<string, string> = { OWNER: "관리자", MEMBER: "멤버" };

interface FarmOverviewProps {
  farmId: string;
}

// 농장 상세 "개요" 탭(이슈 #43) — 농장 정보 표시+수정/삭제만 담당.
// 진단하기/처방받기 풀폭 버튼과 초대코드·멤버 목록은 탭바(진단/처방/멤버)로 이동했다.
export default function FarmOverview({ farmId }: FarmOverviewProps) {
  const router = useRouter();
  const [farm, setFarm] = useState<FarmResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

  const [editing, setEditing] = useState(false);
  const [editName, setEditName] = useState("");
  const [editLocation, setEditLocation] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionBusy, setActionBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getFarm(farmId)
      .then((farmData) => {
        if (cancelled) return;
        setFarm(farmData);
        setEditName(farmData.name);
        setEditLocation(farmData.location ?? "");
      })
      .catch((err) => {
        if (cancelled) return;
        if (isNotFound(err)) {
          setNotFound(true);
        } else {
          setLoadError(resolveErrorMessage(err));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [farmId]);

  if (notFound) {
    return (
      <p className="text-sm text-zinc-500 dark:text-zinc-400">
        농장을 찾을 수 없거나 접근 권한이 없습니다.{" "}
        <Link href="/farms" className="underline">
          농장 목록으로
        </Link>
      </p>
    );
  }

  if (loadError) {
    return <p className="text-sm text-red-600 dark:text-red-400">{loadError}</p>;
  }

  if (!farm) {
    return <p className="text-sm text-zinc-500 dark:text-zinc-400">불러오는 중...</p>;
  }

  const isOwner = farm.myRole === "OWNER";

  async function handleEditSubmit() {
    if (editName.length < VALIDATION.farmName.minLength || editName.length > VALIDATION.farmName.maxLength) {
      setActionError(
        `농장명은 ${VALIDATION.farmName.minLength}~${VALIDATION.farmName.maxLength}자여야 합니다.`
      );
      return;
    }
    setActionError(null);
    setActionBusy(true);
    try {
      const updated = await updateFarm(farmId, { name: editName, location: editLocation || undefined });
      setFarm(updated);
      setEditing(false);
    } catch (err) {
      setActionError(resolveErrorMessage(err));
    } finally {
      setActionBusy(false);
    }
  }

  async function handleDelete() {
    if (!window.confirm("정말 이 농장을 삭제하시겠습니까? 되돌릴 수 없습니다.")) return;
    setActionBusy(true);
    try {
      await deleteFarm(farmId);
      notifyFarmsChanged(); // Sidebar(#42) 농장 리스트 재조회(죽은 링크 잔존 방지)
      router.push("/farms");
      router.refresh();
    } catch (err) {
      setActionError(resolveErrorMessage(err));
      setActionBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-6 px-6 py-6">
      <EnvironmentWidget farmId={farmId} />
      <div className="grid gap-6 md:grid-cols-2">
        <ForecastWidget farmId={farmId} />
        <VpdWidget farmId={farmId} />
      </div>
      <EnvironmentHistoryChart farmId={farmId} />

      <section className="flex flex-col gap-3 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
        {editing ? (
          <div className="flex flex-col gap-3">
            <FormField
              id="edit-farm-name"
              label="농장명"
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
            />
            <FormField
              id="edit-farm-location"
              label="위치"
              value={editLocation}
              onChange={(e) => setEditLocation(e.target.value)}
            />
            <div className="flex gap-2">
              <button
                type="button"
                disabled={actionBusy}
                onClick={handleEditSubmit}
                className="rounded-md bg-zinc-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900"
              >
                저장
              </button>
              <button
                type="button"
                onClick={() => setEditing(false)}
                className="rounded-md border border-zinc-300 px-3 py-1.5 text-sm dark:border-zinc-700"
              >
                취소
              </button>
            </div>
          </div>
        ) : (
          <>
            <div className="flex items-center justify-between">
              <h2 className="text-xl font-semibold text-zinc-900 dark:text-zinc-50">{farm.name}</h2>
              <span className="rounded bg-zinc-100 px-2 py-0.5 text-xs text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">
                {ROLE_LABELS[farm.myRole] ?? farm.myRole}
              </span>
            </div>
            <dl className="grid grid-cols-2 gap-2 text-sm text-zinc-600 dark:text-zinc-400">
              <div>
                <dt className="text-zinc-400 dark:text-zinc-500">작물</dt>
                <dd>{CROP_LABELS[farm.cropType] ?? farm.cropType}</dd>
              </div>
              <div>
                <dt className="text-zinc-400 dark:text-zinc-500">위치</dt>
                <dd>{farm.location || "-"}</dd>
              </div>
              <div>
                <dt className="text-zinc-400 dark:text-zinc-500">멤버 수</dt>
                <dd>{farm.memberCount}명</dd>
              </div>
            </dl>
            {isOwner && (
              <div className="flex gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => setEditing(true)}
                  className="rounded-md border border-zinc-300 px-3 py-1.5 text-sm dark:border-zinc-700"
                >
                  수정
                </button>
                <button
                  type="button"
                  disabled={actionBusy}
                  onClick={handleDelete}
                  className="rounded-md border border-red-300 px-3 py-1.5 text-sm text-red-600 disabled:opacity-60 dark:border-red-900 dark:text-red-400"
                >
                  농장 삭제
                </button>
              </div>
            )}
          </>
        )}
      </section>

      {actionError && <p className="text-sm text-red-600 dark:text-red-400">{actionError}</p>}

      {isOwner && <EnvThresholdForm farmId={farmId} />}
    </div>
  );
}
