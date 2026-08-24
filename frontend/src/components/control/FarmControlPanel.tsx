"use client";

import { useEffect, useState } from "react";
import ZoneControlPanel from "@/components/control/ZoneControlPanel";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { getFarm } from "@/lib/api/farms";
import { getZoneTree } from "@/lib/api/zones";
import type { FarmResponse, ZoneTreeResponse } from "@/types";

interface FarmControlPanelProps {
  farmId: string;
}

// 제어 탭(이슈 #108, contract §4.12) — 제어는 존 단위라 존 선택 UI가 먼저 필요하다(GET /zones 트리
// 재사용). 존이 0개인 농장은 정상 케이스이므로 안내만 하고 끝낸다(장비 탭에서 먼저 존을 만들어야 함).
export default function FarmControlPanel({ farmId }: FarmControlPanelProps) {
  const [farm, setFarm] = useState<FarmResponse | null>(null);
  const [tree, setTree] = useState<ZoneTreeResponse | null>(null);
  const [treeError, setTreeError] = useState<string | null>(null);
  const [zoneId, setZoneId] = useState<number | null>(null);

  useEffect(() => {
    getFarm(farmId)
      .then(setFarm)
      .catch(() => {
        // 비상 정지 버튼(OWNER 전용) 노출 여부에만 쓰인다 — 실패해도 화면 자체는 계속 보여준다.
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

  const isOwner = farm?.myRole === "OWNER";

  return (
    <div className="flex flex-col gap-4 px-6 py-6">
      <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">제어</h2>

      {treeError && <p className="text-sm text-red-600 dark:text-red-400">{treeError}</p>}

      {!tree && !treeError && <p className="text-sm text-zinc-500 dark:text-zinc-400">불러오는 중...</p>}

      {tree && tree.zones.length === 0 && (
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          등록된 존이 없습니다. 장비 탭에서 존을 먼저 추가해주세요.
        </p>
      )}

      {tree && tree.zones.length > 0 && (
        <>
          <div className="flex flex-wrap gap-2">
            {tree.zones.map((zone) => (
              <button
                key={zone.id}
                type="button"
                aria-pressed={zoneId === zone.id}
                onClick={() => setZoneId(zone.id)}
                className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
                  zoneId === zone.id
                    ? "border-zinc-900 bg-zinc-900 text-white dark:border-zinc-50 dark:bg-zinc-50 dark:text-zinc-900"
                    : "border-zinc-300 bg-white text-zinc-600 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-400"
                }`}
              >
                {zone.name}
              </button>
            ))}
          </div>

          {/* zoneId가 바뀌면 완전히 새로 마운트 — 이전 존의 초안 입력값·배너 상태가 새 존으로 새지 않게 */}
          {zoneId !== null && <ZoneControlPanel key={zoneId} farmId={farmId} zoneId={zoneId} isOwner={isOwner} />}
        </>
      )}
    </div>
  );
}
