"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Chip } from "@/components/monitoring/ui";
import Modal from "@/components/ui/Modal";
import {
  acknowledgeAllAlarmEvents,
  acknowledgeAlarmEvent,
  addAlarmMemo,
  getAlarmEvent,
  getAlarmRule,
  listAlarmEvents,
  resolveAlarmEvent,
} from "@/lib/api/alarms";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { getFarm } from "@/lib/api/farms";
import { getZoneTree } from "@/lib/api/zones";
import { notifyAlarmsChanged } from "@/lib/alarmsBus";
import { describeAlarmScope, summarizeAlarmRule } from "@/lib/alarmDisplay";
import { hasFarmRoleAtLeast } from "@/lib/roles";
import { buildLocationMaps, type LocationMaps } from "@/lib/zoneTree";
import type {
  AlarmEventDetailResponse,
  AlarmEventResponse,
  AlarmRuleResponse,
  FarmResponse,
} from "@/types";
import AlarmDetailPanel from "./AlarmDetailPanel";
import AlarmList from "./AlarmList";
import {
  EMPTY_FILTER_COUNTS,
  FILTER_CHIPS,
  filterToQuery,
  type AlarmFilterKey,
  type FilterCounts,
} from "./filters";
import MobileAlarmList from "./MobileAlarmList";

const PAGE_SIZE = 20;

interface AlarmsPageClientProps {
  farmId: string;
}

// 알람 현황 화면(이슈 #136) 오케스트레이터 — 필터·목록·상세 패널·전체 확인 처리를 조합한다.
// 행 클릭은 페이지 이동 없이 상세 패널만 갱신하고(선택 상태는 필터가 바뀌어도 유지), 확인/처리
// 완료/메모/전체확인은 OPERATOR 이상만 가능하다(#122/#123 원칙 — VIEWER는 버튼 자체를 숨긴다).
export default function AlarmsPageClient({ farmId }: AlarmsPageClientProps) {
  const [farm, setFarm] = useState<FarmResponse | null>(null);
  const [locationMaps, setLocationMaps] = useState<LocationMaps | null>(null);

  const [filter, setFilter] = useState<AlarmFilterKey>("ALL");
  const [items, setItems] = useState<AlarmEventResponse[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [page, setPage] = useState(0);

  const [filterCounts, setFilterCounts] =
    useState<FilterCounts>(EMPTY_FILTER_COUNTS);
  // "전체 확인 처리" 버튼 게이팅 전용(리뷰 P3-1) — filterCounts.ALL(상태 무관 전체 건수)과
  // 달리 미확인 건수만 담는다.
  const [unacknowledgedTotal, setUnacknowledgedTotal] = useState<number | null>(
    null,
  );

  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<AlarmEventDetailResponse | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  const [rule, setRule] = useState<AlarmRuleResponse | null>(null);
  const [ruleLoading, setRuleLoading] = useState(false);

  const [actionBusy, setActionBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const [memoOpen, setMemoOpen] = useState(false);
  const [memoSubmitting, setMemoSubmitting] = useState(false);
  const [memoError, setMemoError] = useState<string | null>(null);

  const [ackAllOpen, setAckAllOpen] = useState(false);
  const [ackAllSubmitting, setAckAllSubmitting] = useState(false);
  const [ackAllError, setAckAllError] = useState<string | null>(null);

  // 모바일 카드 스택(이슈 #147, 시안 m3-alarm) — 상세 패널을 둘 공간이 없어 탭하면 오버레이로
  // 연다. 데스크톱은 selectedId가 바뀌면 우측 패널이 그 자리에서 갱신되지만, 모바일은 명시적으로
  // 열고 닫는다(뒤로가기 개념이 필요해서 desktop과 동일한 selectedId 흐름 위에 열림 상태만 얹는다).
  const [mobileDetailOpen, setMobileDetailOpen] = useState(false);

  const canWrite = hasFarmRoleAtLeast(farm?.myRole, "OPERATOR");

  // 농장명(위치 표기 "FARM"일 때)·존 트리(위치 조합용). 둘 다 실패해도 위치는 "—"로 대체될
  // 뿐 화면 전체를 막지 않는다(핸드오프: 조회 실패 시 "—").
  useEffect(() => {
    let cancelled = false;
    getFarm(farmId)
      .then((f) => {
        if (!cancelled) setFarm(f);
      })
      .catch(() => {});
    getZoneTree(farmId)
      .then((tree) => {
        if (!cancelled) setLocationMaps(buildLocationMaps(tree));
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [farmId]);

  // "전체" 칩 카운트는 severity=CRITICAL/WARNING 두 값만 존재하는 것을 이용해 별도 조회 없이
  // 합산으로 구한다(AlarmSeverity enum, 백엔드 entity/AlarmSeverity.java). 그 자리에서 절약한
  // 호출로 미확인(UNACKNOWLEDGED) 건수를 직접 조회해 "전체 확인 처리" 버튼을 정확히 게이팅한다
  // (리뷰 P3-1 — status=RESOLVED와 별개 축이라 ALL - RESOLVED로는 구할 수 없다).
  const loadFilterCounts = useCallback(async () => {
    const [critical, warning, resolved, unacknowledged] =
      await Promise.allSettled([
        listAlarmEvents(farmId, { severity: "CRITICAL" }, 0, 1),
        listAlarmEvents(farmId, { severity: "WARNING" }, 0, 1),
        listAlarmEvents(farmId, { status: "RESOLVED" }, 0, 1),
        listAlarmEvents(farmId, { status: "UNACKNOWLEDGED" }, 0, 1),
      ]);
    const criticalCount =
      critical.status === "fulfilled" ? critical.value.totalElements : null;
    const warningCount =
      warning.status === "fulfilled" ? warning.value.totalElements : null;
    setFilterCounts({
      ALL:
        criticalCount !== null && warningCount !== null
          ? criticalCount + warningCount
          : null,
      CRITICAL: criticalCount,
      WARNING: warningCount,
      RESOLVED:
        resolved.status === "fulfilled" ? resolved.value.totalElements : null,
    });
    setUnacknowledgedTotal(
      unacknowledged.status === "fulfilled"
        ? unacknowledged.value.totalElements
        : null,
    );
  }, [farmId]);

  useEffect(() => {
    async function run() {
      await loadFilterCounts();
    }
    run();
  }, [loadFilterCounts]);

  // 필터를 빠르게 연속 전환하면(전체→경보→완료) 응답이 요청 순서대로 돌아온다는 보장이 없어
  // 늦게 도착한 이전 필터 응답이 나중 응답을 덮어쓸 수 있다(리뷰 P2-1). detail/rule effect의
  // 지역 cancelled 플래그와 동일한 목적이지만, loadList는 이 effect 외에 onRefresh·전체확인
  // 후에도 호출되는 공유 함수라 호출마다 고유한 요청 ID를 발급해 "가장 최근에 시작된 요청만
  // 화면에 반영"되도록 한다(가장 나중에 도착한 응답이 아니라 가장 나중에 시작된 요청 기준).
  const listRequestIdRef = useRef(0);

  const loadList = useCallback(
    async (targetFilter: AlarmFilterKey) => {
      const requestId = ++listRequestIdRef.current;
      setListLoading(true);
      setListError(null);
      try {
        const res = await listAlarmEvents(
          farmId,
          filterToQuery(targetFilter),
          0,
          PAGE_SIZE,
        );
        if (listRequestIdRef.current !== requestId) return; // 그 사이 더 최신 요청이 시작됨 — 폐기
        setItems(res.content);
        setPage(0);
        setTotalElements(res.totalElements);
        // 필터가 바뀌어도 이전 선택은 유지한다(시안 Interactions) — 아직 아무것도 선택되지
        // 않았을 때만(최초 로드) 첫 행을 기본 선택한다.
        setSelectedId((prev) => prev ?? res.content[0]?.id ?? null);
      } catch (err) {
        if (listRequestIdRef.current !== requestId) return;
        setListError(resolveErrorMessage(err));
        setItems([]);
        setTotalElements(0);
      } finally {
        if (listRequestIdRef.current === requestId) setListLoading(false);
      }
    },
    [farmId],
  );

  useEffect(() => {
    async function run() {
      await loadList(filter);
    }
    run();
  }, [filter, loadList]);

  async function handleLoadMore() {
    setLoadingMore(true);
    try {
      const nextPage = page + 1;
      const res = await listAlarmEvents(
        farmId,
        filterToQuery(filter),
        nextPage,
        PAGE_SIZE,
      );
      setItems((prev) => [...prev, ...res.content]);
      setPage(nextPage);
      setTotalElements(res.totalElements);
    } catch (err) {
      setListError(resolveErrorMessage(err));
    } finally {
      setLoadingMore(false);
    }
  }

  // 상세 패널 — 선택된 알람이 바뀔 때마다 새로 조회한다. selectedId가 null로 바뀌면(예: 목록이
  // 비어 아무 것도 선택되지 않음) 렌더 중 조정 패턴으로 비운다(react-hooks/set-state-in-effect 회피).
  const [detailForId, setDetailForId] = useState(selectedId);
  if (selectedId !== detailForId) {
    setDetailForId(selectedId);
    if (selectedId === null) setDetail(null);
  }

  useEffect(() => {
    if (selectedId === null) return;
    let cancelled = false;

    async function load() {
      setDetailLoading(true);
      setDetailError(null);
      try {
        const d = await getAlarmEvent(farmId, selectedId!);
        if (!cancelled) setDetail(d);
      } catch (err) {
        if (!cancelled) setDetailError(resolveErrorMessage(err));
      } finally {
        if (!cancelled) setDetailLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [farmId, selectedId]);

  // 규칙 요약 — ruleId가 있을 때만 조회하고, 실패하면 행 자체를 생략한다(지어내지 않는다).
  const ruleId = detail?.event.ruleId ?? null;
  const [ruleForId, setRuleForId] = useState(ruleId);
  if (ruleId !== ruleForId) {
    setRuleForId(ruleId);
    if (ruleId === null) setRule(null);
  }

  useEffect(() => {
    if (ruleId === null) return;
    let cancelled = false;

    async function load() {
      setRuleLoading(true);
      try {
        const r = await getAlarmRule(farmId, ruleId!);
        if (!cancelled) setRule(r);
      } catch {
        if (!cancelled) setRule(null);
      } finally {
        if (!cancelled) setRuleLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [farmId, ruleId]);

  async function refreshSelectedDetail() {
    if (selectedId === null) return;
    try {
      const fresh = await getAlarmEvent(farmId, selectedId);
      setDetail(fresh);
    } catch {
      // 상세 갱신 실패는 조용히 넘어간다 — 목록/배지는 이미 갱신됐고, 패널은 다음 선택 시 재조회된다.
    }
  }

  // id 기반으로 뺐다(리뷰 대비: 모바일 카드 스택의 "확인" 액션은 상세 패널을 열지 않고도 동작해야
  // 해서 detail.event.id에 묶인 원래 시그니처로는 재사용이 안 됐다). 데스크톱 상세 패널의
  // handleAcknowledge는 이 함수를 detail.event.id로 호출하는 얇은 래퍼로 남긴다(동작 무변경).
  async function handleAcknowledgeId(id: number) {
    setActionBusy(true);
    setActionError(null);
    try {
      const updated = await acknowledgeAlarmEvent(farmId, id);
      setItems((prev) =>
        prev.map((it) => (it.id === updated.id ? updated : it)),
      );
      if (selectedId === id) await refreshSelectedDetail();
      notifyAlarmsChanged();
      loadFilterCounts();
    } catch (err) {
      setActionError(resolveErrorMessage(err));
    } finally {
      setActionBusy(false);
    }
  }

  function handleAcknowledge() {
    if (!detail) return;
    return handleAcknowledgeId(detail.event.id);
  }

  async function handleResolve() {
    if (!detail) return;
    setActionBusy(true);
    setActionError(null);
    try {
      const updated = await resolveAlarmEvent(farmId, detail.event.id);
      setItems((prev) =>
        prev.map((it) => (it.id === updated.id ? updated : it)),
      );
      await refreshSelectedDetail();
      notifyAlarmsChanged();
      loadFilterCounts();
    } catch (err) {
      setActionError(resolveErrorMessage(err));
    } finally {
      setActionBusy(false);
    }
  }

  async function handleMemoSubmit(note: string) {
    if (!detail) return;
    setMemoSubmitting(true);
    setMemoError(null);
    try {
      const updated = await addAlarmMemo(farmId, detail.event.id, note);
      setDetail(updated);
      setMemoOpen(false);
    } catch (err) {
      setMemoError(resolveErrorMessage(err));
    } finally {
      setMemoSubmitting(false);
    }
  }

  async function handleAcknowledgeAll() {
    setAckAllSubmitting(true);
    setAckAllError(null);
    try {
      await acknowledgeAllAlarmEvents(farmId);
      setAckAllOpen(false);
      await loadList(filter);
      await refreshSelectedDetail();
      notifyAlarmsChanged();
      loadFilterCounts();
    } catch (err) {
      setAckAllError(resolveErrorMessage(err));
    } finally {
      setAckAllSubmitting(false);
    }
  }

  const location = detail
    ? describeAlarmScope(detail.event, locationMaps, farm?.name ?? null)
    : "";
  const ruleSummary = rule ? summarizeAlarmRule(rule) : null;

  return (
    <>
      {/* 모바일(<768px, 이슈 #147, 시안 m3-alarm) — 카드 스택 + 탭하면 오버레이 상세. 같은
          items/필터/detail 상태를 desktop과 공유한다(재조회 없음). */}
      <div className="flex flex-col min-[768px]:hidden">
        <MobileAlarmList
          farmId={farmId}
          filter={filter}
          items={items}
          filterCounts={filterCounts}
          loading={listLoading}
          loadingMore={loadingMore}
          totalElements={totalElements}
          error={listError}
          canWrite={canWrite}
          actionBusy={actionBusy}
          unacknowledgedTotal={unacknowledgedTotal}
          onSelect={(id) => {
            setSelectedId(id);
            setMobileDetailOpen(true);
          }}
          onFilterChange={setFilter}
          onAcknowledge={handleAcknowledgeId}
          onAcknowledgeAll={() => setAckAllOpen(true)}
          onLoadMore={handleLoadMore}
          onShowAll={() => setFilter("ALL")}
          onRefresh={() => loadList(filter)}
          locationMaps={locationMaps}
          farmName={farm?.name ?? null}
        />
      </div>

      <Modal
        open={mobileDetailOpen}
        onClose={() => setMobileDetailOpen(false)}
        title="알람 상세"
      >
        <AlarmDetailPanel
          farmId={farmId}
          detail={detail}
          loading={detailLoading}
          error={detailError}
          location={location}
          ruleSummary={ruleSummary}
          ruleLoading={ruleLoading}
          canWrite={canWrite}
          actionBusy={actionBusy}
          actionError={actionError}
          onAcknowledge={handleAcknowledge}
          onResolve={handleResolve}
          memoOpen={memoOpen}
          memoSubmitting={memoSubmitting}
          memoError={memoError}
          onMemoOpen={() => setMemoOpen(true)}
          onMemoClose={() => setMemoOpen(false)}
          onMemoSubmit={handleMemoSubmit}
        />
      </Modal>

      {/* 데스크톱·태블릿(≥768px) — 기존 구성 그대로(회귀 금지). */}
      <div className="hidden flex-col gap-4 px-6 py-6 min-[768px]:flex">
        <div className="flex flex-wrap items-center gap-2.5">
          <h1 className="text-[17px] leading-[1.2] font-bold text-dp-ink">
            알람 현황
          </h1>
          <div className="hidden flex-1 min-[768px]:block" />
          <div className="flex gap-1.5 overflow-x-auto">
            {FILTER_CHIPS.map((chip) => {
              const count = filterCounts[chip.key];
              return (
                <Chip
                  key={chip.key}
                  as="button"
                  active={chip.key === filter}
                  tone={chip.tone}
                  onClick={() => setFilter(chip.key)}
                >
                  {chip.label}
                  {count !== null ? ` ${count}` : ""}
                </Chip>
              );
            })}
          </div>
          {canWrite && (
            <button
              type="button"
              onClick={() => setAckAllOpen(true)}
              disabled={unacknowledgedTotal === 0}
              className="rounded-[7px] border border-dp-line-strong px-[13px] py-[7px] text-[12px] leading-none font-medium whitespace-nowrap text-dp-body disabled:opacity-40"
            >
              전체 확인 처리
            </button>
          )}
        </div>

        <div className="grid gap-3.5 min-[1440px]:grid-cols-[1fr_340px] min-[1920px]:grid-cols-[1fr_440px]">
          <AlarmList
            filter={filter}
            items={items}
            totalElements={totalElements}
            loading={listLoading}
            loadingMore={loadingMore}
            error={listError}
            selectedId={selectedId}
            onSelect={setSelectedId}
            onLoadMore={handleLoadMore}
            onShowAll={() => setFilter("ALL")}
            onRefresh={() => loadList(filter)}
            locationMaps={locationMaps}
            farmName={farm?.name ?? null}
          />

          <AlarmDetailPanel
            farmId={farmId}
            detail={detail}
            loading={detailLoading}
            error={detailError}
            location={location}
            ruleSummary={ruleSummary}
            ruleLoading={ruleLoading}
            canWrite={canWrite}
            actionBusy={actionBusy}
            actionError={actionError}
            onAcknowledge={handleAcknowledge}
            onResolve={handleResolve}
            memoOpen={memoOpen}
            memoSubmitting={memoSubmitting}
            memoError={memoError}
            onMemoOpen={() => setMemoOpen(true)}
            onMemoClose={() => setMemoOpen(false)}
            onMemoSubmit={handleMemoSubmit}
          />
        </div>
      </div>

      {/* 데스크톱·모바일 공용 — MobileAlarmList의 "전체 확인" 버튼도 같은 ackAllOpen을 연다. */}
      <Modal
        open={ackAllOpen}
        onClose={() => setAckAllOpen(false)}
        title="전체 확인 처리"
      >
        <div className="flex flex-col gap-3">
          <p className="text-sm text-zinc-700 dark:text-zinc-300">
            미확인 알람을 모두 확인 처리합니다. 이 작업은 되돌릴 수 없습니다.
          </p>
          {ackAllError && (
            <p className="text-sm text-red-600 dark:text-red-400">
              {ackAllError}
            </p>
          )}
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setAckAllOpen(false)}
              className="rounded-md border border-zinc-300 px-3 py-1.5 text-sm text-zinc-700 dark:border-zinc-700 dark:text-zinc-300"
            >
              취소
            </button>
            <button
              type="button"
              onClick={handleAcknowledgeAll}
              disabled={ackAllSubmitting}
              className="rounded-md bg-dp-ink px-3 py-1.5 text-sm font-medium text-dp-surface disabled:opacity-60"
            >
              {ackAllSubmitting ? "처리 중..." : "전체 확인 처리"}
            </button>
          </div>
        </div>
      </Modal>
    </>
  );
}
