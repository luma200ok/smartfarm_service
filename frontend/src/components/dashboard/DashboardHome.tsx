"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import FarmDashboardCard from "@/components/dashboard/FarmDashboardCard";
import FarmRackPanel from "@/components/dashboard/FarmRackPanel";
import FarmTrendChart from "@/components/dashboard/FarmTrendChart";
import { Card, Chip } from "@/components/monitoring/ui";
import { getFarmBriefing } from "@/lib/api/briefing";
import { getDashboardFarms } from "@/lib/api/dashboard";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import type {
  DashboardFarmsResponse,
  FarmBriefingResponse,
  FarmDashboardStatus,
} from "@/types";

type SortMode = "status" | "name";

const STATUS_RANK: Record<FarmDashboardStatus, number> = {
  CRITICAL: 0,
  WARNING: 1,
  NORMAL: 2,
};

interface TodoItem {
  key: string;
  text: string;
  meta: string;
  tone: "critical" | "warning" | "neutral";
  href: string;
}

// 홈 대시보드(이슈 #142, 시안 `01-dashboard-home`) — 이슈 #99가 만든 이전 DashboardHome을
// 대체한다. 대체 전 확인한, 이전 화면이 겸하던 역할:
//  - 농장 카드 그리드 + 선택 시 하단 갱신(페이지 이동 없음) → 이 화면이 그대로 이어받는다.
//  - 랙 도면(FarmRackPanel)·24시간 추이(FarmTrendChart) → 컴포넌트를 그대로 재사용한다.
//  - "실측 환경"(EnvironmentWidget, KMA 외기 + 내부 제어값 — 이 화면에서 유일한 비-시뮬레이션
//    소스였다, 이슈 #99 리뷰) → 시안에 이 위젯이 없어 여기서는 뺐다. 역할 자체는 잃지 않는다 —
//    농장 상세 "농장별 현황"(FarmOverview, `/farms/{id}`, 좌측 내비 01-02)이 이미 같은
//    EnvironmentWidget을 쓰고 있어(components/farms/FarmOverview.tsx) 그쪽에서 계속 볼 수 있다.
//
// 카드 데이터는 GET /api/dashboard/farms(#139) 하나로 전부 온다 — 예전처럼 농장마다 zones·
// devices를 개별 호출하지 않는다(N+1 방지가 그 API를 만든 이유, #142 handoff).
export default function DashboardHome() {
  // 이슈 #140 — 응답이 평문 배열에서 { farms, totalCount, truncated } 래퍼로 바뀌었다.
  // 카드 그리드·정렬·"오늘 할일"은 전부 farms 배열만 참조하므로 아래에서 파생시켜 그대로
  // 재사용하고(dashboardData가 null이면 farms도 null — 로딩 상태 판정이 그대로 유지된다),
  // totalCount·truncated는 절단 안내에서만 쓴다.
  const [dashboardData, setDashboardData] = useState<DashboardFarmsResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selectedFarmId, setSelectedFarmId] = useState<number | null>(null);
  const [sortMode, setSortMode] = useState<SortMode>("status");
  const [briefing, setBriefing] = useState<FarmBriefingResponse | null>(null);

  const farms = dashboardData?.farms ?? null;

  useEffect(() => {
    getDashboardFarms()
      .then((data) => {
        setDashboardData(data);
        setSelectedFarmId((prev) => prev ?? (data.farms.length > 0 ? data.farms[0].id : null));
      })
      .catch((err) => setError(resolveErrorMessage(err)));
  }, []);

  // "보정 기한 임박" pill·할 일의 보정 항목은 선택한 농장 1건만 조회한다 — GET /briefing은
  // 농장 단건 스코프라, 농장 수만큼 반복 호출하면 #139가 없애려던 N+1을 이 브리핑에서
  // 재생산하는 셈이다(#142 handoff 원칙). 그래서 "농장 전체 합산"이 아니라 "선택 농장 기준"임을
  // pill·항목 문구에 농장명으로 명시한다.
  useEffect(() => {
    // selectedFarmId는 farms 로딩 전 잠깐만 null이고, 일단 세팅되면 다시 null로 돌아가지
    // 않는다(농장 목록이 페이지 생애주기 중 바뀌지 않으므로) — 그 잠깐 동안은 briefing의 초기값
    // null을 그대로 두면 된다. 여기서 setBriefing(null)을 동기 호출하면 렌더 연쇄를 유발한다는
    // 린트 경고(react-hooks/set-state-in-effect)가 있어, null 분기는 그냥 skip한다.
    if (selectedFarmId === null) return;
    let cancelled = false;
    getFarmBriefing(selectedFarmId)
      .then((res) => {
        if (!cancelled) setBriefing(res);
      })
      .catch(() => {
        if (!cancelled) setBriefing(null);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedFarmId]);

  const selectedFarm = farms?.find((f) => f.id === selectedFarmId) ?? null;

  const sortedFarms = useMemo(() => {
    if (!farms) return [];
    const copy = [...farms];
    if (sortMode === "name") {
      copy.sort((a, b) => a.name.localeCompare(b.name, "ko"));
    } else {
      copy.sort(
        (a, b) => STATUS_RANK[a.status] - STATUS_RANK[b.status] || a.name.localeCompare(b.name, "ko")
      );
    }
    return copy;
  }, [farms, sortMode]);

  // "조치 필요 N곳" — 이미 불러온 dashboardFarms에서 파생한다(추가 호출 없음). 시안의 "N곳"은
  // 농장(위치) 수 단위인데 GET /briefing의 actionRequiredCount는 농장 "1곳"의 이벤트 건수라
  // 단위가 다르다(백엔드 FarmBriefingResponse javadoc) — 그래서 briefing이 아니라 카드 상태를
  // 직접 센다. 정확하고, 추가 API 호출도 없다.
  const actionRequiredFarmCount = farms?.filter((f) => f.status !== "NORMAL").length ?? 0;

  const todoItems = useMemo<TodoItem[]>(() => {
    if (!farms) return [];
    const items: TodoItem[] = farms
      .filter((f) => f.status !== "NORMAL")
      .sort((a, b) => STATUS_RANK[a.status] - STATUS_RANK[b.status])
      .map((f) => ({
        key: `alarm-${f.id}`,
        text: f.latestAlarmMessage ?? "확인이 필요한 이상 신호가 있습니다.",
        meta: f.name,
        tone: f.status === "CRITICAL" ? "critical" : "warning",
        href: `/farms/${f.id}/alarms`,
      }));
    if (selectedFarm && briefing && briefing.calibrationDueSoonCount > 0) {
      items.push({
        key: "calibration",
        text: `보정 기한 임박 장비 ${briefing.calibrationDueSoonCount}대`,
        meta: selectedFarm.name,
        tone: "neutral",
        href: `/farms/${selectedFarm.id}/devices`,
      });
    }
    return items;
  }, [farms, briefing, selectedFarm]);

  return (
    <div className="flex flex-1 flex-col">
      {/* 상단 GlobalBar(이슈 #133)가 로고·탭을 상시 노출하므로 별도 페이지 제목 헤더는 두지 않는다. */}
      <main className="flex flex-1 flex-col gap-4 bg-dp-canvas px-4 py-4 min-[768px]:px-5 min-[1440px]:px-6 min-[1440px]:py-5">
        {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

        {!error && farms === null && <p className="text-sm text-dp-muted">불러오는 중...</p>}

        {!error && farms !== null && farms.length === 0 && (
          <>
            <h1 className="text-[19px] leading-[1.2] font-bold text-dp-ink">오늘 아침 브리핑</h1>
            <Card className="flex flex-col items-start gap-3 p-5">
              <p className="text-sm text-dp-sub">
                아직 등록된 농장이 없습니다. 농장을 만들거나 초대코드로 합류해보세요.
              </p>
              <Link
                href="/farms"
                className="rounded-md bg-dp-green px-4 py-2 text-[13px] font-semibold text-dp-on-green"
              >
                농장 만들기 / 합류하기
              </Link>
            </Card>
          </>
        )}

        {!error && farms !== null && farms.length > 0 && (
          <>
            {/* 1층 — 브리핑 헤더 행 */}
            <div className="flex flex-none flex-wrap items-center gap-3.5">
              <h1 className="text-[19px] leading-[1.2] font-bold text-dp-ink">오늘 아침 브리핑</h1>

              <span
                className={`inline-flex items-center gap-2 rounded-full border px-[13px] py-[7px] text-[12px] leading-none font-semibold ${
                  actionRequiredFarmCount > 0
                    ? "border-dp-red-line bg-dp-surface text-dp-red-ink"
                    : "border-dp-line-strong bg-dp-surface text-dp-body"
                }`}
              >
                {actionRequiredFarmCount > 0 && <span className="h-[7px] w-[7px] rounded-full bg-dp-red" />}
                조치 필요 {actionRequiredFarmCount}곳
              </span>

              {briefing && briefing.calibrationDueSoonCount > 0 && (
                <span className="inline-flex items-center gap-2 rounded-full border border-dp-line-strong bg-dp-surface px-[13px] py-[7px] text-[12px] leading-none font-medium text-dp-body">
                  {/* ⚠️ 이 수치만 스코프가 다르다 — "조치 필요"는 전 농장 파생인데 보정 임박은
                      /briefing 이 농장 단건 스코프라 선택 농장 1건 기준이다(#139 N+1 회피).
                      농장이 여럿일 때 이름만 붙이면 "그 농장 소속"인지 "그 농장 기준"인지
                      모호해서 "기준"까지 명시한다(#142 리뷰 LOW). */}
                  보정 기한 임박 {briefing.calibrationDueSoonCount}대
                  {farms.length > 1 && selectedFarm ? ` · ${selectedFarm.name} 기준` : ""}
                </span>
              )}

              <div className="flex-1" />

              <div className="flex gap-[5px]">
                <Chip as="button" size="sm" active={sortMode === "status"} onClick={() => setSortMode("status")}>
                  상태순
                </Chip>
                <Chip as="button" size="sm" active={sortMode === "name"} onClick={() => setSortMode("name")}>
                  이름순
                </Chip>
              </div>
            </div>

            {/* 절단 안내(이슈 #140) — 백엔드 집계 상한(dashboard.max-farms) 초과로 카드가
                잘렸을 때만 뜬다. 상한 숫자는 FE가 모르므로 지어내지 않고 서버가 준 실측치
                (totalCount·farms.length)만 쓴다. 경고가 아니라 정보라 red/amber 대신
                기존 blue 톤(FarmDataAnalysis의 정보성 배지와 동일 패턴)을 쓴다. */}
            {dashboardData?.truncated && (
              <p
                role="status"
                className="flex-none rounded-md border border-dp-blue bg-dp-blue-tint px-3 py-2 text-xs leading-relaxed text-dp-blue-ink"
              >
                농장 {dashboardData.totalCount}개 중 {farms.length}개만 표시하고 있습니다.
              </p>
            )}

            {/* 2층 — 농장 카드 그리드. auto-fill이라 농장 1·2·3·4+개 어느 쪽이든 카드 크기를
                유지한 채 자연스럽게 줄바꿈된다(고정 4열 대신 — 시안은 4개 전제라 repeat(4,1fr)
                였지만 데모 계정은 1개라 그대로 쓰면 카드가 억지로 늘어난다, #142 handoff). */}
            <div
              className="grid flex-none gap-3.5"
              style={{ gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))" }}
            >
              {sortedFarms.map((farm) => (
                <FarmDashboardCard
                  key={farm.id}
                  farm={farm}
                  selected={farm.id === selectedFarmId}
                  onSelect={() => setSelectedFarmId(farm.id)}
                />
              ))}
            </div>

            {/* 3층 — 선택 농장 상세(랙 도면 · 추이 · 할 일/바로가기). 카드 클릭은 selectedFarmId만
                바꾸므로 페이지 이동 없이 이 구역만 갱신된다. */}
            {selectedFarm && (
              <div className="grid min-h-0 flex-1 gap-3.5 min-[1280px]:grid-cols-[1.35fr_1fr_360px]">
                <FarmRackPanel farmId={selectedFarm.id} farmName={selectedFarm.name} />
                <FarmTrendChart farmId={selectedFarm.id} farmName={selectedFarm.name} />
                <DashboardSidePanel farmId={selectedFarm.id} todoItems={todoItems} />
              </div>
            )}
          </>
        )}
      </main>
    </div>
  );
}

function todoToneClass(tone: TodoItem["tone"]): string {
  if (tone === "critical") return "bg-dp-red";
  if (tone === "warning") return "bg-dp-amber";
  return "bg-dp-disabled";
}

// 할 일(좌측 상단부터 미확인 알람 순) + 바로가기(전부 실제 존재하는 화면만 — 시안의 "날씨
// 예보"·"일일 리포트"·"농약 정보"는 nav-config.ts에서 아직 disabled라 링크를 만들지 않는다,
// #142 handoff "위젯 편집" 금지와 같은 원칙).
function DashboardSidePanel({ farmId, todoItems }: { farmId: number; todoItems: TodoItem[] }) {
  return (
    <div className="flex min-h-0 flex-col gap-3">
      <Card className="flex flex-none flex-col gap-1 p-4">
        <div className="mb-1.5 flex items-baseline">
          <span className="text-[13.5px] font-semibold text-dp-ink">할 일</span>
          <div className="flex-1" />
          <span className="text-[11.5px] text-dp-muted">{todoItems.length}건</span>
        </div>
        {todoItems.length === 0 ? (
          <p className="py-2 text-[12px] text-dp-sub">확인이 필요한 항목이 없습니다.</p>
        ) : (
          <ul className="flex flex-col">
            {todoItems.map((item) => (
              <li key={item.key} className="border-b border-dp-line-row last:border-b-0">
                <Link href={item.href} className="flex gap-2.5 py-2 hover:opacity-80">
                  <span className={`w-[5px] flex-none rounded-[3px] ${todoToneClass(item.tone)}`} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[12.5px] leading-[1.4] font-semibold text-dp-ink">
                      {item.text}
                    </span>
                    <span className="mt-0.5 block text-[11px] leading-none text-dp-muted">{item.meta}</span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card className="flex min-h-0 flex-1 flex-col gap-2.5 p-4">
        <span className="text-[13.5px] font-semibold text-dp-ink">바로가기</span>
        <div className="grid grid-cols-2 gap-2">
          <Link
            href={`/farms/${farmId}/chat`}
            className="rounded-[7px] border border-dp-green-line bg-dp-green-tint py-[11px] text-center text-[12px] font-semibold text-dp-green-ink"
          >
            AI 챗봇
          </Link>
          <Link
            href={`/farms/${farmId}/data`}
            className="rounded-[7px] border border-dp-line bg-dp-inset py-[11px] text-center text-[12px] font-semibold text-dp-body"
          >
            데이터 분석
          </Link>
          <Link
            href={`/farms/${farmId}/devices`}
            className="rounded-[7px] border border-dp-line bg-dp-inset py-[11px] text-center text-[12px] font-semibold text-dp-body"
          >
            장비 관리
          </Link>
          <Link
            href={`/farms/${farmId}/alarms`}
            className="rounded-[7px] border border-dp-line bg-dp-inset py-[11px] text-center text-[12px] font-semibold text-dp-body"
          >
            알람 현황
          </Link>
        </div>
      </Card>
    </div>
  );
}
