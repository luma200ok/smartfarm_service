"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { CardTitle, StatusBadge } from "@/components/monitoring/ui";
import { ROLE_LABELS } from "@/constants";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { listMembers } from "@/lib/api/farms";
import type { MemberResponse } from "@/types";

interface FarmMembersPreviewProps {
  farmId: string;
  /** 초대 발급은 ADMIN 전용(이슈 #123) — 링크만 노출하고 실제 발급 폼은 /members 전체 화면을 재사용한다. */
  isAdmin: boolean;
}

const AVATAR_COLOR: Record<string, string> = {
  ADMIN: "bg-dp-green",
  PENDING: "bg-dp-disabled",
};

// 사용자 · 권한 위젯(시안 06, 이슈 #144) — 관리(장비·센서) 화면 우측 상단에 붙는 축약 미리보기.
// 전체 초대·역할변경·제거 기능은 이미 FarmMembers(/members)에 있어 재중복 구현하지 않고
// "초대" 링크만 그 화면으로 보낸다(handoff — 기존 화면이 있으면 재사용, 없으면 만들지 않는다).
export default function FarmMembersPreview({ farmId, isAdmin }: FarmMembersPreviewProps) {
  const [members, setMembers] = useState<MemberResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    listMembers(farmId)
      .then((res) => {
        if (!cancelled) setMembers(res);
      })
      .catch((err) => {
        if (!cancelled) setError(resolveErrorMessage(err));
      });
    return () => {
      cancelled = true;
    };
  }, [farmId]);

  return (
    <div className="flex flex-col rounded-[10px] border border-dp-line bg-dp-surface p-4">
      <div className="mb-2.5 flex items-baseline">
        <CardTitle as="h3">사용자 · 권한{members ? ` (${members.length})` : ""}</CardTitle>
        <div className="flex-1" />
        {isAdmin && (
          <Link href={`/farms/${farmId}/members`} className="text-[11.5px] font-semibold text-dp-green">
            초대
          </Link>
        )}
      </div>

      {error && <p className="text-sm text-dp-red-ink">{error}</p>}
      {!error && members === null && <p className="text-sm text-dp-sub">불러오는 중...</p>}
      {!error && members !== null && members.length === 0 && <p className="text-sm text-dp-sub">멤버가 없습니다.</p>}

      {!error && members && members.length > 0 && (
        <ul className="max-h-[280px] overflow-y-auto">
          {members.map((m) => (
            <li key={m.memberId} className="flex items-center gap-2.5 border-b border-dp-line-row py-[9px] last:border-0">
              <span
                className={`flex h-7 w-7 flex-none items-center justify-center rounded-full text-[11px] font-semibold text-white ${
                  AVATAR_COLOR[m.role] ?? "bg-dp-neutral"
                }`}
              >
                {m.nickname.slice(0, 1)}
              </span>
              <span className="flex-1 truncate text-[12.5px] font-semibold text-dp-ink">{m.nickname}</span>
              <StatusBadge label={ROLE_LABELS[m.role]} tone={m.pending ? "warning" : "neutral"} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
