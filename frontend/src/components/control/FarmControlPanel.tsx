"use client";

import { useEffect, useState } from "react";
import { Chip } from "@/components/monitoring/ui";
import MobileZoneControl from "@/components/control/MobileZoneControl";
import ZoneControlPanel from "@/components/control/ZoneControlPanel";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { getFarm } from "@/lib/api/farms";
import { getZoneTree } from "@/lib/api/zones";
import { hasFarmRoleAtLeast } from "@/lib/roles";
import type { FarmResponse, ZoneTreeResponse } from "@/types";

interface FarmControlPanelProps {
  farmId: string;
}

// 제어 탭(이슈 #108, contract §4.12) — 제어는 존 단위라 존 선택 UI가 먼저 필요하다(GET /zones 트리
// 재사용). 존이 0개인 농장은 정상 케이스이므로 안내만 하고 끝낸다(장비 탭에서 먼저 존을 만들어야 함).
//
// 표현은 --dp-* 토큰 기반 공용 프리미티브(components/monitoring/ui.tsx)를 따른다(이슈 #108 리뷰
// P2 — 다음 작업인 #109 라우트 디자인 통일과 팔레트를 맞춰둔다).
export default function FarmControlPanel({ farmId }: FarmControlPanelProps) {
  const [farm, setFarm] = useState<FarmResponse | null>(null);
  const [tree, setTree] = useState<ZoneTreeResponse | null>(null);
  const [treeError, setTreeError] = useState<string | null>(null);
  const [zoneId, setZoneId] = useState<number | null>(null);

  useEffect(() => {
    getFarm(farmId)
      .then(setFarm)
      .catch(() => {
        // 비상 정지 버튼(OPERATOR 이상) 노출 여부에만 쓰인다 — 실패해도 화면 자체는 계속 보여준다.
      });
  }, [farmId]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setTreeError(null);
      try {
        const res = await getZoneTree(farmId);
        if (cancelled) return;
        setTree(res);
        setZoneId((prev) => prev ?? res.zones[0]?.id ?? null);
      } catch (err) {
        if (!cancelled) setTreeError(resolveErrorMessage(err));
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [farmId]);

  // 비상 정지·제어 조작은 OPERATOR 이상(contract §2, 이슈 #123).
  const canControl = hasFarmRoleAtLeast(farm?.myRole, "OPERATOR");

  return (
    <>
      {/* 데스크톱·태블릿(≥768px) — 기존 구성 그대로(회귀 금지). */}
      <div className="hidden flex-col gap-4 px-6 py-6 min-[768px]:flex">
        <h2 className="text-sm font-semibold text-dp-ink">제어</h2>

        {treeError && <p className="text-sm text-dp-red-ink">{treeError}</p>}

        {!tree && !treeError && (
          <p className="text-sm text-dp-muted">불러오는 중...</p>
        )}

        {tree && tree.zones.length === 0 && (
          <p className="text-sm text-dp-muted">
            등록된 존이 없습니다. 장비 탭에서 존을 먼저 추가해주세요.
          </p>
        )}

        {tree && tree.zones.length > 0 && (
          <>
            <div className="flex flex-wrap gap-1.5">
              {tree.zones.map((zone) => (
                <Chip
                  key={zone.id}
                  as="button"
                  size="sm"
                  active={zoneId === zone.id}
                  onClick={() => setZoneId(zone.id)}
                >
                  {zone.name}
                </Chip>
              ))}
            </div>

            {/* zoneId가 바뀌면 완전히 새로 마운트 — 이전 존의 초안 입력값·배너 상태가 새 존으로 새지 않게 */}
            {zoneId !== null && (
              <ZoneControlPanel
                key={zoneId}
                farmId={farmId}
                zoneId={zoneId}
                canControl={canControl}
              />
            )}
          </>
        )}
      </div>

      {/* 모바일(<768px, 이슈 #147, 시안 m2-control) — 같은 zoneId/canControl 상태를 재사용,
          렌더만 다르다(MobileZoneControl이 useZoneControl 훅을 desktop과 공유). */}
      <div className="flex flex-col gap-3 px-4 py-3 min-[768px]:hidden">
        {treeError && <p className="text-sm text-dp-red-ink">{treeError}</p>}

        {!tree && !treeError && (
          <p className="text-sm text-dp-muted">불러오는 중...</p>
        )}

        {tree && tree.zones.length === 0 && (
          <p className="text-sm text-dp-muted">
            등록된 존이 없습니다. 장비 탭에서 존을 먼저 추가해주세요.
          </p>
        )}

        {tree && tree.zones.length > 0 && (
          <>
            {/* 터치 타깃 44px(핸드오프 §1, 리뷰 P2 — #111 재발 방지) — 데스크톱용 Chip
                size="sm"(~24px)를 그대로 쓰지 않고 MobileAlarmList의 필터 버튼과 같은
                min-h-11 패턴으로 맞춘다. */}
            <div className="flex flex-wrap gap-1.5">
              {tree.zones.map((zone) => {
                const active = zoneId === zone.id;
                return (
                  <button
                    key={zone.id}
                    type="button"
                    onClick={() => setZoneId(zone.id)}
                    className={`min-h-11 flex-none rounded-[7px] px-[13px] text-[12px] font-medium whitespace-nowrap ${
                      active
                        ? "bg-dp-ink text-dp-surface"
                        : "border border-dp-line-strong text-dp-body"
                    }`}
                  >
                    {zone.name}
                  </button>
                );
              })}
            </div>

            {zoneId !== null && (
              <MobileZoneControl
                key={zoneId}
                farmId={farmId}
                zoneId={zoneId}
                canControl={canControl}
              />
            )}
          </>
        )}
      </div>
    </>
  );
}
