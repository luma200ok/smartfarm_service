"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Card, CardTitle, StatusBadge } from "@/components/monitoring/ui";
import { ROLE_LABELS } from "@/constants";
import { resolveErrorMessage, isNotFound } from "@/lib/api/errorMessage";
import { getMe } from "@/lib/api/auth";
import { createInvitation, getFarm, listMembers, removeMember, updateMemberRole } from "@/lib/api/farms";
import { hasFarmRoleAtLeast } from "@/lib/roles";
import type { FarmResponse, FarmRole, InvitationResponse, MemberResponse } from "@/types";

// 역할 변경 select에 노출할 부여 가능 역할 전부(PENDING 포함 — 승인 보류로 되돌리는 수단, 이슈 #122).
const ASSIGNABLE_ROLES: FarmRole[] = ["ADMIN", "OPERATOR", "VIEWER", "PENDING"];

interface FarmMembersProps {
  farmId: string;
}

// 농장 상세 "멤버" 탭(이슈 #43) — 기존 FarmDetail의 초대코드 발급+멤버 목록을
// 로직 무변경으로 이동/분리했다.
// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card·CardTitle·StatusBadge)로 통일한다(이슈 #109).
export default function FarmMembers({ farmId }: FarmMembersProps) {
  const router = useRouter();
  const [farm, setFarm] = useState<FarmResponse | null>(null);
  const [members, setMembers] = useState<MemberResponse[] | null>(null);
  const [myUserId, setMyUserId] = useState<number | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

  const [actionError, setActionError] = useState<string | null>(null);
  const [actionBusy, setActionBusy] = useState(false);

  const [invitation, setInvitation] = useState<InvitationResponse | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setMyUserId(null); // farmId 전환 시 이전 농장의 본인 판별값 잔존 방지 (getMe는 뒤늦게 갱신됨)
      try {
        const [farmData, memberData] = await Promise.all([getFarm(farmId), listMembers(farmId)]);
        if (cancelled) return;
        setFarm(farmData);
        setMembers(memberData);
      } catch (err) {
        if (cancelled) return;
        if (isNotFound(err)) {
          setNotFound(true);
        } else {
          setLoadError(resolveErrorMessage(err));
        }
        return;
      }

      // getMe는 별도 처리 — 단독 실패해도 farm·members는 이미 정상 렌더된 상태를 유지하고
      // myUserId=null로 남겨 본인 식별이 필요한 UI(탈퇴 버튼)만 숨긴다.
      try {
        const me = await getMe();
        if (!cancelled) setMyUserId(me.id);
      } catch {
        // no-op — myUserId는 null 유지
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [farmId]);

  if (notFound) {
    return (
      <p className="px-6 py-6 text-sm text-dp-sub">
        농장을 찾을 수 없거나 접근 권한이 없습니다.{" "}
        <Link href="/farms" className="underline">
          농장 목록으로
        </Link>
      </p>
    );
  }

  if (loadError) {
    return <p className="px-6 py-6 text-sm text-dp-red-ink">{loadError}</p>;
  }

  if (!farm || !members) {
    return <p className="px-6 py-6 text-sm text-dp-sub">불러오는 중...</p>;
  }

  // 멤버 관리(초대 발급·역할 변경·승인·제거)는 ADMIN 전용(contract §2, 이슈 #123).
  const isAdmin = hasFarmRoleAtLeast(farm.myRole, "ADMIN");

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
      const [memberData, farmData] = await Promise.all([listMembers(farmId), getFarm(farmId)]);
      setMembers(memberData);
      setFarm(farmData);
    } catch (err) {
      setActionError(resolveErrorMessage(err));
    } finally {
      setActionBusy(false);
    }
  }

  // 역할 변경(이슈 #122/#123) — 초대 수락자(PENDING) 승인도 이 호출 하나로 처리한다.
  // 마지막 ADMIN 강등 등 위험한 전이는 서버가 F006으로 최종 차단한다(버튼 노출은 보조 UX).
  async function handleRoleChange(memberId: number, role: FarmRole) {
    setActionError(null);
    setActionBusy(true);
    try {
      await updateMemberRole(farmId, memberId, role);
      const [memberData, farmData] = await Promise.all([listMembers(farmId), getFarm(farmId)]);
      setMembers(memberData);
      setFarm(farmData);
    } catch (err) {
      setActionError(resolveErrorMessage(err));
    } finally {
      setActionBusy(false);
    }
  }

  // 본인 탈퇴 (contract §3 "ADMIN 또는 본인" — 마지막 ADMIN 본인은 F006으로 서버가 차단, 탈퇴 후 농장 목록으로 이동, 이슈 #123).
  async function handleLeaveFarm(memberId: number) {
    if (!window.confirm("정말 이 농장에서 탈퇴하시겠습니까?")) return;
    setActionError(null);
    setActionBusy(true);
    try {
      await removeMember(farmId, memberId);
      router.push("/farms");
      router.refresh();
    } catch (err) {
      setActionError(resolveErrorMessage(err));
      setActionBusy(false);
    }
  }

  const pendingCount = members.filter((m) => m.pending).length;

  return (
    <div className="flex flex-col gap-6 px-6 py-6">
      {isAdmin && (
        <Card as="section" className="flex flex-col gap-2 p-4">
          <CardTitle as="h3">초대코드 발급</CardTitle>
          <button
            type="button"
            disabled={actionBusy}
            onClick={handleInvite}
            className="self-start rounded-md border border-dp-line-strong px-3 py-1.5 text-sm text-dp-body disabled:opacity-60"
          >
            초대코드 발급
          </button>
          {invitation && (
            <p className="text-sm text-dp-sub">
              코드: <span className="font-mono font-semibold text-dp-ink">{invitation.code}</span> (만료:{" "}
              {new Date(invitation.expiresAt).toLocaleString()})
            </p>
          )}
        </Card>
      )}

      <Card as="section" className="flex flex-col gap-2 p-4">
        <CardTitle as="h3">
          멤버 ({members.length}){pendingCount > 0 && ` · 승인 대기 ${pendingCount}명`}
        </CardTitle>
        <ul className="flex flex-col gap-2">
          {members.map((m) => {
            const isSelf = myUserId !== null && m.userId === myUserId;
            return (
              <li
                key={m.memberId}
                className={`flex flex-wrap items-center justify-between gap-2 rounded-md p-2 text-sm ${
                  m.pending ? "bg-dp-amber-tint" : ""
                }`}
              >
                <span className="flex flex-col gap-0.5">
                  <span className="flex items-center gap-2">
                    <span className="text-dp-ink">{m.nickname}</span>
                    <StatusBadge
                      label={`${ROLE_LABELS[m.role] ?? m.role}${isSelf ? " · 나" : ""}`}
                      tone={m.pending ? "warning" : "neutral"}
                    />
                  </span>
                  {m.pending && isAdmin && (
                    <span className="text-xs text-dp-amber-deep">역할을 지정하면 가입이 승인됩니다.</span>
                  )}
                </span>
                <span className="flex items-center gap-3">
                  {isAdmin && (
                    <select
                      aria-label={`${m.nickname} 역할 변경`}
                      value={m.role}
                      disabled={actionBusy}
                      onChange={(e) => handleRoleChange(m.memberId, e.target.value as FarmRole)}
                      className="rounded-md border border-dp-line-strong bg-dp-surface px-2 py-1 text-xs text-dp-body disabled:opacity-60"
                    >
                      {ASSIGNABLE_ROLES.map((role) => (
                        <option key={role} value={role}>
                          {ROLE_LABELS[role]}
                        </option>
                      ))}
                    </select>
                  )}
                  {isAdmin && m.role !== "ADMIN" && !isSelf && (
                    <button
                      type="button"
                      disabled={actionBusy}
                      onClick={() => handleRemoveMember(m.memberId)}
                      className="text-xs text-dp-red-ink hover:underline disabled:opacity-60"
                    >
                      제거
                    </button>
                  )}
                  {isSelf && m.role !== "ADMIN" && (
                    <button
                      type="button"
                      disabled={actionBusy}
                      onClick={() => handleLeaveFarm(m.memberId)}
                      className="text-xs text-dp-red-ink hover:underline disabled:opacity-60"
                    >
                      탈퇴하기
                    </button>
                  )}
                </span>
              </li>
            );
          })}
        </ul>
      </Card>

      {actionError && <p className="text-sm text-dp-red-ink">{actionError}</p>}
    </div>
  );
}
