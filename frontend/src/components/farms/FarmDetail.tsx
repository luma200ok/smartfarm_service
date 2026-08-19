"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import FormField from "@/components/ui/FormField";
import { VALIDATION } from "@/constants";
import { resolveErrorMessage, isNotFound } from "@/lib/api/errorMessage";
import { createInvitation, deleteFarm, getFarm, listMembers, removeMember, updateFarm } from "@/lib/api/farms";
import type { FarmResponse, InvitationResponse, MemberResponse } from "@/types";

const CROP_LABELS: Record<string, string> = { TOMATO: "토마토" };
const ROLE_LABELS: Record<string, string> = { OWNER: "관리자", MEMBER: "멤버" };

interface FarmDetailProps {
  farmId: string;
}

export default function FarmDetail({ farmId }: FarmDetailProps) {
  const router = useRouter();
  const [farm, setFarm] = useState<FarmResponse | null>(null);
  const [members, setMembers] = useState<MemberResponse[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

  const [editing, setEditing] = useState(false);
  const [editName, setEditName] = useState("");
  const [editLocation, setEditLocation] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionBusy, setActionBusy] = useState(false);

  const [invitation, setInvitation] = useState<InvitationResponse | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const [farmData, memberData] = await Promise.all([getFarm(farmId), listMembers(farmId)]);
        if (cancelled) return;
        setFarm(farmData);
        setMembers(memberData);
        setEditName(farmData.name);
        setEditLocation(farmData.location ?? "");
      } catch (err) {
        if (cancelled) return;
        if (isNotFound(err)) {
          setNotFound(true);
        } else {
          setLoadError(resolveErrorMessage(err));
        }
      }
    }
    load();
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

  if (!farm || !members) {
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
      router.push("/farms");
      router.refresh();
    } catch (err) {
      setActionError(resolveErrorMessage(err));
      setActionBusy(false);
    }
  }

  async function handleInvite() {
    setActionError(null);
    setActionBusy(true);
    try {
      const inv = await createInvitation(farmId);
      setInvitation(inv);
    } catch (err) {
      setActionError(resolveErrorMessage(err));
    } finally {
      setActionBusy(false);
    }
  }

  async function handleRemoveMember(memberId: number) {
    if (!window.confirm("이 멤버를 농장에서 제거하시겠습니까?")) return;
    setActionError(null);
    setActionBusy(true);
    try {
      await removeMember(farmId, memberId);
      const memberData = await listMembers(farmId);
      setMembers(memberData);
    } catch (err) {
      setActionError(resolveErrorMessage(err));
    } finally {
      setActionBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-6 px-6 py-6">
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

      <section className="flex gap-3">
        <Link
          href={`/farms/${farmId}/diagnoses`}
          className="flex-1 rounded-lg border border-zinc-200 px-4 py-3 text-center text-sm font-medium hover:bg-zinc-50 dark:border-zinc-800 dark:hover:bg-zinc-900"
        >
          진단하기
        </Link>
        <Link
          href={`/farms/${farmId}/prescriptions`}
          className="flex-1 rounded-lg border border-zinc-200 px-4 py-3 text-center text-sm font-medium hover:bg-zinc-50 dark:border-zinc-800 dark:hover:bg-zinc-900"
        >
          처방받기
        </Link>
      </section>

      {isOwner && (
        <section className="flex flex-col gap-2 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
          <h3 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">초대코드 발급</h3>
          <button
            type="button"
            disabled={actionBusy}
            onClick={handleInvite}
            className="self-start rounded-md border border-zinc-300 px-3 py-1.5 text-sm disabled:opacity-60 dark:border-zinc-700"
          >
            초대코드 발급
          </button>
          {invitation && (
            <p className="text-sm text-zinc-600 dark:text-zinc-400">
              코드: <span className="font-mono font-semibold text-zinc-900 dark:text-zinc-50">{invitation.code}</span>{" "}
              (만료: {new Date(invitation.expiresAt).toLocaleString()})
            </p>
          )}
        </section>
      )}

      <section className="flex flex-col gap-2 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
        <h3 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">멤버 ({members.length})</h3>
        <ul className="flex flex-col gap-2">
          {members.map((m) => (
            <li key={m.memberId} className="flex items-center justify-between text-sm">
              <span>
                {m.nickname} <span className="text-zinc-400 dark:text-zinc-500">({ROLE_LABELS[m.role] ?? m.role})</span>
              </span>
              {isOwner && m.role !== "OWNER" && (
                <button
                  type="button"
                  disabled={actionBusy}
                  onClick={() => handleRemoveMember(m.memberId)}
                  className="text-xs text-red-600 hover:underline disabled:opacity-60 dark:text-red-400"
                >
                  제거
                </button>
              )}
            </li>
          ))}
        </ul>
      </section>

      {actionError && <p className="text-sm text-red-600 dark:text-red-400">{actionError}</p>}
    </div>
  );
}
