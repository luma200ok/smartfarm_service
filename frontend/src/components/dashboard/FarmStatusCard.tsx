"use client";

import { useEffect, useState } from "react";
import { Card, StatusBadge } from "@/components/monitoring/ui";
import type { PreviewSeverity as Severity } from "@/types";
import { getDeviceSummary } from "@/lib/api/devices";
import { getZoneTree } from "@/lib/api/zones";
import type { DeviceSummaryResponse, FarmSummaryResponse, ZoneTreeResponse } from "@/types";

const CROP_LABELS: Record<string, string> = { TOMATO: "토마토" };
const ROLE_LABELS: Record<string, string> = { OWNER: "관리자", MEMBER: "멤버" };

interface FarmStatusCardProps {
  farm: FarmSummaryResponse;
  selected: boolean;
  onSelect: () => void;
}

function summaryTone(summary: DeviceSummaryResponse | null): Severity {
  if (!summary) return "done";
  if (summary.faultOrOffline > 0) return "critical";
  if (summary.warning > 0) return "warning";
  return "done";
}

function summaryLabel(summary: DeviceSummaryResponse | null): string {
  if (!summary) return "-";
  if (summary.faultOrOffline > 0) return `경보 ${summary.faultOrOffline}`;
  if (summary.warning > 0) return `주의 ${summary.warning}`;
  return "정상";
}

// 홈 대시보드 농장 카드(이슈 #99) — 존·랙 구조(§4.10)와 장비 요약(§4.10)만으로 채운다.
// 프리뷰 목업의 작물명·정식일수·7일 추이·브리핑 문구는 대응 API가 없어(handoff 요건 2) 뺐다.
export default function FarmStatusCard({ farm, selected, onSelect }: FarmStatusCardProps) {
  const [tree, setTree] = useState<ZoneTreeResponse | null>(null);
  const [summary, setSummary] = useState<DeviceSummaryResponse | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    Promise.all([getZoneTree(farm.id), getDeviceSummary(farm.id)])
      .then(([treeRes, summaryRes]) => {
        if (cancelled) return;
        setTree(treeRes);
        setSummary(summaryRes);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });
    return () => {
      cancelled = true;
    };
  }, [farm.id]);

  const rackCount = tree ? tree.zones.reduce((sum, z) => sum + z.racks.length, 0) : null;
  const levelCount = tree
    ? tree.zones.reduce((sum, z) => sum + z.racks.reduce((s, r) => s + r.levelCount, 0), 0)
    : null;

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className="text-left"
    >
      <Card
        className={`flex flex-col gap-3 px-4 py-4 transition-colors ${
          selected ? "border-[1.5px] border-dp-green" : ""
        }`}
      >
        <div className="flex items-start gap-2.5">
          <div className="min-w-0 flex-1">
            <div className="truncate text-[15px] leading-[1.3] font-bold text-dp-ink">{farm.name}</div>
            <div className="mt-1 flex items-center gap-1.5 text-[11.5px] leading-none text-dp-muted">
              <span>{CROP_LABELS[farm.cropType] ?? farm.cropType}</span>
              <span>·</span>
              <span>{ROLE_LABELS[farm.myRole] ?? farm.myRole}</span>
            </div>
          </div>
          <StatusBadge label={summaryLabel(summary)} tone={summaryTone(summary)} />
        </div>

        {failed ? (
          <p className="text-[11.5px] leading-[1.5] text-dp-muted">현황을 불러오지 못했습니다.</p>
        ) : (
          <div className="grid grid-cols-3 gap-2">
            <div>
              <div className="text-[10.5px] leading-none font-medium text-dp-muted">랙</div>
              <div className="mt-1.5 text-[17px] leading-none font-bold text-dp-ink">
                {rackCount ?? "-"}
              </div>
            </div>
            <div>
              <div className="text-[10.5px] leading-none font-medium text-dp-muted">층</div>
              <div className="mt-1.5 text-[17px] leading-none font-bold text-dp-ink">
                {levelCount ?? "-"}
              </div>
            </div>
            <div>
              <div className="text-[10.5px] leading-none font-medium text-dp-muted">장비</div>
              <div className="mt-1.5 text-[17px] leading-none font-bold text-dp-ink">
                {summary ? summary.total : "-"}
              </div>
            </div>
          </div>
        )}
      </Card>
    </button>
  );
}
