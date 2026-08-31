"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Card, StatusBadge } from "@/components/monitoring/ui";
import EnvironmentHistoryChart from "@/components/environment/EnvironmentHistoryChart";
import EnvThresholdForm from "@/components/environment/EnvThresholdForm";
import EnvironmentWidget from "@/components/environment/EnvironmentWidget";
import ForecastWidget from "@/components/environment/ForecastWidget";
import VpdWidget from "@/components/environment/VpdWidget";
import FormField from "@/components/ui/FormField";
import FarmRackPanel from "@/components/dashboard/FarmRackPanel";
import { ROLE_LABELS, SENSOR_METRIC_LABELS, VALIDATION } from "@/constants";
import { resolveErrorMessage, isNotFound } from "@/lib/api/errorMessage";
import { deleteFarm, getFarm, updateFarm } from "@/lib/api/farms";
import { getDashboardFarms } from "@/lib/api/dashboard";
import { notifyFarmsChanged } from "@/lib/farmsBus";
import { formatDashboardMetricValue } from "@/lib/metricFormat";
import { hasFarmRoleAtLeast } from "@/lib/roles";
import { useIsMobileViewport } from "@/lib/useIsMobileViewport";
import type { FarmDashboardResponse, FarmResponse } from "@/types";

const CROP_LABELS: Record<string, string> = { TOMATO: "토마토" };

interface FarmOverviewProps {
  farmId: string;
}

// 농장 상세 "개요" 탭(이슈 #43) — 농장 정보 표시+수정/삭제만 담당.
// 진단하기/처방받기 풀폭 버튼과 초대코드·멤버 목록은 탭바(진단/처방/멤버)로 이동했다.
// 위젯(EnvironmentWidget 등)은 컨테이너인 이 화면이 배치·카드 스타일만 통일하고
// props·조회 로직은 그대로 둔다(이슈 #109 A그룹 특이사항).
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

  // 모바일 홈(이슈 #147, 시안 m1-home) — 데스크톱과 별개 화면이라 데이터도 별개 소스가 필요하다.
  // getDashboardFarms()는 DashboardHome(이슈 #142)이 이미 쓰는 같은 API 래퍼다 — 지표 3열×2행과
  // 랙 배치도 축약에 필요한 값(metrics·rackCount 등)이 GET /farms/{id}(FarmResponse)엔 없어서
  // 새로 fetch가 아니라 "이미 있는 함수를 이 화면에서도 부른다"에 해당한다(재사용 원칙).
  // 데스크톱에서는 이 목록이 필요 없으니 모바일 뷰포트일 때만 호출한다.
  const isMobile = useIsMobileViewport();
  const [mobileFarm, setMobileFarm] = useState<FarmDashboardResponse | null>(
    null,
  );
  const [mobileFarmError, setMobileFarmError] = useState<string | null>(null);
  const [bannerDismissed, setBannerDismissed] = useState(false);
  // AppShell의 drawerClosedFor와 동일한 "렌더 중 조정" 패턴(react-hooks/set-state-in-effect
  // 회피) — farmId가 바뀌면 이전 농장에서 닫은 배너 상태가 새 농장으로 새지 않게 초기화한다.
  const [bannerDismissedFor, setBannerDismissedFor] = useState(farmId);
  if (farmId !== bannerDismissedFor) {
    setBannerDismissedFor(farmId);
    setBannerDismissed(false);
  }

  useEffect(() => {
    if (!isMobile) return;
    let cancelled = false;
    getDashboardFarms()
      .then((list) => {
        if (cancelled) return;
        // 이슈 #140 — GET /api/dashboard/farms가 평문 배열에서 { farms, totalCount, truncated }
        // 래퍼로 바뀌었다. 이 화면은 절단 안내가 필요 없는 단건 조회(개별 농장 매칭)라
        // list.farms만 쓰면 된다.
        const match = list.farms.find((f) => String(f.id) === farmId) ?? null;
        setMobileFarm(match);
        setMobileFarmError(
          match ? null : "농장 요약 정보를 불러올 수 없습니다.",
        );
      })
      .catch((err) => {
        if (!cancelled) setMobileFarmError(resolveErrorMessage(err));
      });
    return () => {
      cancelled = true;
    };
  }, [isMobile, farmId]);

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
      <p className="text-sm text-dp-sub">
        농장을 찾을 수 없거나 접근 권한이 없습니다.{" "}
        <Link href="/farms" className="underline">
          농장 목록으로
        </Link>
      </p>
    );
  }

  if (loadError) {
    return <p className="text-sm text-dp-red-ink">{loadError}</p>;
  }

  if (!farm) {
    return <p className="text-sm text-dp-sub">불러오는 중...</p>;
  }

  // 구조 변경(농장 정보 수정·삭제)·임계치 폼 마운트는 ADMIN 전용(contract §2, 이슈 #123).
  const isAdmin = hasFarmRoleAtLeast(farm.myRole, "ADMIN");

  async function handleEditSubmit() {
    if (
      editName.length < VALIDATION.farmName.minLength ||
      editName.length > VALIDATION.farmName.maxLength
    ) {
      setActionError(
        `농장명은 ${VALIDATION.farmName.minLength}~${VALIDATION.farmName.maxLength}자여야 합니다.`,
      );
      return;
    }
    setActionError(null);
    setActionBusy(true);
    try {
      const updated = await updateFarm(farmId, {
        name: editName,
        location: editLocation || undefined,
      });
      setFarm(updated);
      setEditing(false);
    } catch (err) {
      setActionError(resolveErrorMessage(err));
    } finally {
      setActionBusy(false);
    }
  }

  async function handleDelete() {
    if (!window.confirm("정말 이 농장을 삭제하시겠습니까? 되돌릴 수 없습니다."))
      return;
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
    <>
      {/* 모바일 홈(이슈 #147, 시안 m1-home) — 데스크톱 "개요" 탭과 완전히 다른 화면 구성(지표
          3열×2행 + 랙 배치도 축약)이라 별도 브랜치로 둔다. 데이터는 위에서 getDashboardFarms()로
          재사용(신규 fetch 아님), 랙 배치도는 FarmRackPanel을 compact 모드로 그대로 재사용한다. */}
      <div className="flex flex-col gap-3 px-4 py-3 min-[768px]:hidden">
        {mobileFarmError && !mobileFarm && (
          <p className="text-sm text-dp-red-ink">{mobileFarmError}</p>
        )}

        {!mobileFarmError && !mobileFarm && (
          <p className="text-sm text-dp-sub">불러오는 중...</p>
        )}

        {mobileFarm && (
          <>
            {mobileFarm.latestAlarmMessage &&
              mobileFarm.status !== "NORMAL" &&
              !bannerDismissed && (
                <div
                  className={`rounded-[9px] border-l-[3px] p-[12px] ${
                    mobileFarm.status === "CRITICAL"
                      ? "border-l-dp-red bg-dp-surface"
                      : "border-l-dp-amber bg-dp-surface"
                  }`}
                >
                  <p
                    className={`text-[13px] leading-[1.4] font-semibold ${
                      mobileFarm.status === "CRITICAL"
                        ? "text-dp-red-ink"
                        : "text-dp-amber-ink"
                    }`}
                  >
                    {mobileFarm.latestAlarmMessage}
                  </p>
                  <div className="mt-2.5 flex gap-2">
                    <Link
                      href={`/farms/${farmId}/alarms`}
                      className={`flex min-h-11 flex-1 items-center justify-center rounded-[7px] text-[12.5px] leading-none font-semibold ${
                        mobileFarm.status === "CRITICAL"
                          ? "bg-dp-red text-white"
                          : "bg-dp-amber text-white"
                      }`}
                    >
                      조치하기
                    </Link>
                    <button
                      type="button"
                      onClick={() => setBannerDismissed(true)}
                      className="flex min-h-11 flex-1 items-center justify-center rounded-[7px] border border-dp-line-strong text-[12.5px] leading-none font-semibold text-dp-body"
                    >
                      나중에
                    </button>
                  </div>
                </div>
              )}

            {mobileFarm.metrics.length > 0 && (
              <div className="grid grid-cols-3 gap-2">
                {mobileFarm.metrics.map((m) => (
                  <div
                    key={m.metric}
                    className="rounded-[9px] border border-dp-line bg-dp-surface p-[11px]"
                  >
                    <div className="text-[11px] leading-none font-medium text-dp-muted">
                      {SENSOR_METRIC_LABELS[m.metric] ?? m.metric}
                    </div>
                    <div
                      className={`mt-[7px] text-[20px] leading-none font-bold ${
                        m.outOfRange ? "text-dp-red-ink" : "text-dp-ink"
                      }`}
                    >
                      {formatDashboardMetricValue(m.metric, m.value)}
                    </div>
                  </div>
                ))}
              </div>
            )}

            <FarmRackPanel
              farmId={mobileFarm.id}
              farmName={mobileFarm.name}
              compact
            />
          </>
        )}
      </div>

      {/* 데스크톱·태블릿(≥768px) "개요" 탭 — 기존 구성 그대로(회귀 금지). */}
      <div className="hidden flex-col gap-6 px-6 py-6 min-[768px]:flex">
        {/* 페이지 제목(이슈 #136 리뷰 P2-2 — 구 FarmTabsHeader 제거 목록에서 개요 화면이 누락돼
          있었다). 카드 안의 <h2>{farm.name}</h2>(아래)는 "농장 정보" 카드 자체의 헤더 겸
          역할 배지 짝이라 남겨둔다 — 페이지 제목(기능명)과 카드 제목(농장명)은 값이 달라
          문자 그대로 중복되지 않는다(다른 화면의 페이지 제목 vs CardTitle 관계와 동일). */}
        <h1 className="text-[17px] leading-[1.2] font-bold text-dp-ink">
          개요
        </h1>
        <EnvironmentWidget farmId={farmId} />
        <div className="grid gap-6 md:grid-cols-2">
          <ForecastWidget farmId={farmId} />
          <VpdWidget farmId={farmId} />
        </div>
        <EnvironmentHistoryChart farmId={farmId} />

        <Card as="section" className="flex flex-col gap-3 p-4">
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
                  className="rounded-md bg-dp-ink px-3 py-1.5 text-sm font-medium text-dp-surface disabled:opacity-40"
                >
                  저장
                </button>
                <button
                  type="button"
                  onClick={() => setEditing(false)}
                  className="rounded-md border border-dp-line-strong px-3 py-1.5 text-sm text-dp-body"
                >
                  취소
                </button>
              </div>
            </div>
          ) : (
            <>
              <div className="flex items-center justify-between">
                <h2 className="text-xl font-semibold text-dp-ink">
                  {farm.name}
                </h2>
                <StatusBadge
                  label={ROLE_LABELS[farm.myRole] ?? farm.myRole}
                  tone={farm.myRole === "PENDING" ? "warning" : "neutral"}
                />
              </div>
              <dl className="grid grid-cols-2 gap-2 text-sm text-dp-sub">
                <div>
                  <dt className="text-dp-faint">작물</dt>
                  <dd>{CROP_LABELS[farm.cropType] ?? farm.cropType}</dd>
                </div>
                <div>
                  <dt className="text-dp-faint">위치</dt>
                  <dd>{farm.location || "-"}</dd>
                </div>
                <div>
                  <dt className="text-dp-faint">멤버 수</dt>
                  <dd>{farm.memberCount}명</dd>
                </div>
              </dl>
              {isAdmin && (
                <div className="flex gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => setEditing(true)}
                    className="rounded-md border border-dp-line-strong px-3 py-1.5 text-sm text-dp-body"
                  >
                    수정
                  </button>
                  <button
                    type="button"
                    disabled={actionBusy}
                    onClick={handleDelete}
                    className="rounded-md border border-dp-red-line px-3 py-1.5 text-sm text-dp-red-ink disabled:opacity-60"
                  >
                    농장 삭제
                  </button>
                </div>
              )}
            </>
          )}
        </Card>

        {actionError && (
          <p className="text-sm text-dp-red-ink">{actionError}</p>
        )}

        {isAdmin && <EnvThresholdForm farmId={farmId} />}
      </div>
    </>
  );
}
