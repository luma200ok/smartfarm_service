"use client";

import { useEffect, useMemo, useState } from "react";
import { Card, CardTitle } from "@/components/monitoring/ui";
import { PESTICIDE_ALERT_SEVERITY_LABELS } from "@/constants";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { listPesticideAlerts, listPesticideReferences } from "@/lib/api/pesticide";
import type { CropType, PesticideAlertResponse, PesticideReferenceResponse } from "@/types";

interface PesticideCardProps {
  cropType: CropType;
}

// 농약 정보 카드(시안 05, 이슈 #128 §4.16) — ⚠️ 안전 고지 필수: source는 이 데이터가 내부 시드
// 샘플이고 실제 농촌진흥청 연동이 아님을 밝힌다. API가 준 문구를 그대로 표시하고 임의로
// "농촌진흥청 연동"처럼 실제 연동을 암시하는 문구로 바꾸지 않는다(계약 §4.16 출처 표기 규칙).
//
// 검색(q)은 결과 상한(참조 50건)이 작아 최초 1회 무필터 조회 후 클라이언트에서 필터링한다 —
// 타이핑마다 서버를 왕복하지 않고, 검색 결과가 0건이어도 출처 고지 문구(1번째 항목의 source)를
// 계속 보여줄 수 있다(N+1 아님 — 목록 1회 조회일 뿐, 항목별 개별 조회가 아니다).
export default function PesticideCard({ cropType }: PesticideCardProps) {
  const [references, setReferences] = useState<PesticideReferenceResponse[] | null>(null);
  const [referencesError, setReferencesError] = useState<string | null>(null);
  const [alerts, setAlerts] = useState<PesticideAlertResponse[] | null>(null);
  const [alertsError, setAlertsError] = useState<string | null>(null);
  const [query, setQuery] = useState("");

  useEffect(() => {
    let cancelled = false;
    listPesticideReferences(cropType)
      .then((res) => {
        if (!cancelled) setReferences(res);
      })
      .catch((err) => {
        if (!cancelled) setReferencesError(resolveErrorMessage(err));
      });
    listPesticideAlerts(cropType)
      .then((res) => {
        if (!cancelled) setAlerts(res);
      })
      .catch((err) => {
        if (!cancelled) setAlertsError(resolveErrorMessage(err));
      });
    return () => {
      cancelled = true;
    };
  }, [cropType]);

  const filtered = useMemo(() => {
    if (!references) return null;
    const trimmed = query.trim().toLowerCase();
    if (!trimmed) return references;
    return references.filter((r) => r.pestName.toLowerCase().includes(trimmed));
  }, [references, query]);

  // 안전 고지(source)는 검색으로 결과가 0건이 되어도 사라지면 안 된다 — 무필터 원본 목록 또는
  // 경보 목록의 첫 항목에서 가져온다(같은 응답 안에서는 항목마다 source가 동일한 고정 문구).
  const disclaimerSource = references?.[0]?.source ?? alerts?.[0]?.source ?? null;

  return (
    <Card as="section" className="flex flex-col p-4">
      <div className="mb-2.5 flex items-baseline">
        <CardTitle as="h3">농약 정보</CardTitle>
      </div>

      <input
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="병해충명으로 검색"
        aria-label="병해충명으로 검색"
        className="mb-2.5 flex-none rounded-md bg-dp-inset px-3 py-2.5 text-xs text-dp-ink placeholder:text-dp-faint"
      />

      <div className="max-h-[320px] overflow-y-auto">
        {referencesError && <p className="text-sm text-dp-red-ink">{referencesError}</p>}
        {!referencesError && references === null && <p className="text-sm text-dp-sub">불러오는 중...</p>}
        {!referencesError && filtered !== null && filtered.length === 0 && (
          <p className="text-sm text-dp-sub">{query.trim() ? "검색 결과가 없습니다." : "등록된 참조정보가 없습니다."}</p>
        )}
        {!referencesError && filtered && filtered.length > 0 && (
          <ul>
            {filtered.map((ref, i) => (
              <li key={`${ref.pestName}-${i}`} className="border-b border-dp-line-row py-[9px] last:border-0">
                <div className="text-[12.5px] font-semibold text-dp-ink">{ref.pestName}</div>
                <div className="mt-1 text-[11.5px] text-dp-muted">
                  등록 약제 {ref.registeredProductCount}종
                  {ref.preHarvestIntervalDays !== null
                    ? ` · 수확 전 ${ref.preHarvestIntervalDays}일`
                    : ref.note
                      ? ` · ${ref.note}`
                      : ""}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="mt-3.5 flex-none border-t border-dp-line pt-3.5">
        <div className="mb-2.5 text-[12.5px] font-semibold text-dp-ink">이번 주 발생 주의</div>
        {alertsError && <p className="text-sm text-dp-red-ink">{alertsError}</p>}
        {!alertsError && alerts === null && <p className="text-sm text-dp-sub">불러오는 중...</p>}
        {!alertsError && alerts !== null && alerts.length === 0 && (
          <p className="text-xs text-dp-sub">현재 발생 중인 경보가 없습니다.</p>
        )}
        {!alertsError && alerts && alerts.length > 0 && (
          <div className="flex flex-col gap-2">
            {alerts.map((alert, i) => (
              <div
                key={i}
                className={`rounded-lg px-3 py-2.5 text-[11.5px] font-medium leading-[1.55] ${
                  alert.severity === "WARNING"
                    ? "border border-dp-amber-line bg-dp-amber-tint text-dp-amber-sub"
                    : "bg-dp-inset text-dp-body"
                }`}
              >
                <span className="mr-1.5 font-semibold">[{PESTICIDE_ALERT_SEVERITY_LABELS[alert.severity]}]</span>
                {alert.message}
              </div>
            ))}
          </div>
        )}
      </div>

      {disclaimerSource && (
        <p className="mt-2.5 flex-none border-t border-dp-line pt-2.5 text-[11px] leading-[1.5] text-dp-faint">
          {disclaimerSource}
        </p>
      )}
    </Card>
  );
}
