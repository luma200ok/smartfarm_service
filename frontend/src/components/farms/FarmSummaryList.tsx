"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Card, StatusBadge } from "@/components/monitoring/ui";
import { ROLE_LABELS } from "@/constants";
import { listFarms, removeMember } from "@/lib/api/farms";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import type { FarmSummaryResponse } from "@/types";

const CROP_LABELS: Record<string, string> = { TOMATO: "토마토" };

interface FarmSummaryListProps {
  emptyHint?: React.ReactNode;
  // 목록 조회 완료(성공) 시 상위 컴포넌트에 결과를 전달 — 대시보드 홈의 환경 위젯이 별도
  // listFarms() 호출 없이 같은 결과를 재사용하도록(이슈 #22, 중복 조회 방지).
  onLoaded?: (farms: FarmSummaryResponse[]) => void;
}

// 내 농장 목록 요약 카드 — 대시보드 홈·농장 목록 화면에서 공용으로 쓴다.
// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card)로 통일한다(이슈 #109).
//
// 승인 대기(PENDING) 항목은 스스로 대기를 취소할 수 있다(이슈 #122/#123) — memberId(본인
// 멤버십 id, FarmSummaryResponse 전용 필드)로 DELETE /api/farms/{farmId}/members/{memberId}를
// 호출한다. 되돌릴 수 없는 동작이라 브라우저 confirm()/alert() 대신 인라인 2단계 확인으로
// 처리한다(리뷰 지시) — 대기 취소 버튼을 누르면 같은 자리에 "예, 취소/아니오"가 나타난다.
export default function FarmSummaryList({ emptyHint, onLoaded }: FarmSummaryListProps) {
  const [farms, setFarms] = useState<FarmSummaryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [confirmFarmId, setConfirmFarmId] = useState<number | null>(null);
  const [cancelingFarmId, setCancelingFarmId] = useState<number | null>(null);
  const [cancelErrorFarmId, setCancelErrorFarmId] = useState<number | null>(null);
  const [cancelError, setCancelError] = useState<string | null>(null);

  // load()는 최초 조회와 대기 취소 성공 후 재조회에서 공유한다.
  const load = useCallback(() => {
    return listFarms().then((data) => {
      setFarms(data);
      onLoaded?.(data);
    });
    // onLoaded는 상위가 매 렌더 새 함수를 넘길 수 있어 deps에 넣지 않는다(재조회 루프 방지 —
    // 기존 마운트 1회 조회와 동일한 관례).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    let cancelled = false;
    load().catch((err) => {
      if (!cancelled) setError(resolveErrorMessage(err));
    });
    return () => {
      cancelled = true;
    };
  }, [load]);

  async function handleCancelPending(farm: FarmSummaryResponse) {
    setCancelError(null);
    setCancelErrorFarmId(null);
    setCancelingFarmId(farm.id);
    try {
      await removeMember(farm.id, farm.memberId);
      setConfirmFarmId(null);
      await load();
    } catch (err) {
      setCancelErrorFarmId(farm.id);
      setCancelError(resolveErrorMessage(err));
    } finally {
      setCancelingFarmId(null);
    }
  }

  if (error) {
    return <p className="text-sm text-dp-red-ink">{error}</p>;
  }

  if (farms === null) {
    return <p className="text-sm text-dp-sub">불러오는 중...</p>;
  }

  if (farms.length === 0) {
    return <p className="text-sm text-dp-sub">{emptyHint ?? "아직 등록된 농장이 없습니다."}</p>;
  }

  return (
    <ul className="flex flex-col gap-2">
      {farms.map((farm) => {
        const isPending = farm.myRole === "PENDING";
        const confirming = confirmFarmId === farm.id;
        const canceling = cancelingFarmId === farm.id;
        return (
          <li key={farm.id} className="flex flex-col gap-1.5">
            <Link href={`/farms/${farm.id}`}>
              <Card className="flex items-center justify-between px-4 py-3 text-sm transition-colors hover:bg-dp-inset">
                <span className="font-medium text-dp-ink">{farm.name}</span>
                <span className="flex items-center gap-2 text-dp-sub">
                  <span>{CROP_LABELS[farm.cropType] ?? farm.cropType}</span>
                  <StatusBadge
                    label={ROLE_LABELS[farm.myRole] ?? farm.myRole}
                    tone={isPending ? "warning" : "neutral"}
                  />
                </span>
              </Card>
            </Link>

            {isPending && (
              <div className="flex flex-wrap items-center gap-2 px-1">
                {confirming ? (
                  <>
                    <span className="text-xs text-dp-amber-deep">
                      정말 가입 대기를 취소할까요? 되돌릴 수 없습니다.
                    </span>
                    <button
                      type="button"
                      disabled={canceling}
                      onClick={() => handleCancelPending(farm)}
                      className="text-xs font-semibold text-dp-red-ink hover:underline disabled:opacity-60"
                    >
                      {canceling ? "취소 중..." : "예, 취소"}
                    </button>
                    <button
                      type="button"
                      disabled={canceling}
                      onClick={() => setConfirmFarmId(null)}
                      className="text-xs text-dp-sub hover:underline disabled:opacity-60"
                    >
                      아니오
                    </button>
                  </>
                ) : (
                  <button
                    type="button"
                    onClick={() => {
                      setCancelError(null);
                      setCancelErrorFarmId(null);
                      setConfirmFarmId(farm.id);
                    }}
                    className="text-xs text-dp-red-ink hover:underline"
                  >
                    대기 취소
                  </button>
                )}
              </div>
            )}
            {cancelErrorFarmId === farm.id && cancelError && (
              <p className="px-1 text-xs text-dp-red-ink">{cancelError}</p>
            )}
          </li>
        );
      })}
    </ul>
  );
}
