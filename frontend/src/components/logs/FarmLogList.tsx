"use client";

import { useEffect, useState } from "react";
import FarmLogForm from "./FarmLogForm";
import { Card, CardTitle, StatusBadge } from "@/components/monitoring/ui";
import Modal from "@/components/ui/Modal";
import { FARM_LOG_TYPE_LABELS } from "@/constants";
import { getMe } from "@/lib/api/auth";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { getFarm } from "@/lib/api/farms";
import { createFarmLog, deleteFarmLog, listFarmLogs, updateFarmLog } from "@/lib/api/farmLogs";
import type { FarmLogRequest, FarmLogResponse, FarmResponse, PageResponse } from "@/types";

interface FarmLogListProps {
  farmId: string;
}

const PAGE_SIZE = 10;

// 작업일지 탭(이슈 #57) — 목록·작성·수정·삭제를 한 컴포넌트에서 오케스트레이션한다
// (FarmMembers·FarmOverview와 동일한 "탭 하나 = 컴포넌트 하나" 관례).
// 수정=작성자 본인만, 삭제=작성자 본인 또는 OWNER — 버튼 노출은 보조판정, 최종은 서버 L002.
export default function FarmLogList({ farmId }: FarmLogListProps) {
  const [farm, setFarm] = useState<FarmResponse | null>(null);
  const [myUserId, setMyUserId] = useState<number | null>(null);
  const [page, setPage] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);
  const [data, setData] = useState<PageResponse<FarmLogResponse> | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<FarmLogResponse | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [rowError, setRowError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  useEffect(() => {
    getFarm(farmId)
      .then(setFarm)
      .catch(() => {
        // 헤더(FarmTabsHeader)가 이미 농장 로드 실패를 처리하므로 여기서는 조용히 넘어간다.
      });
    getMe()
      .then((me) => setMyUserId(me.id))
      .catch(() => {
        // myUserId=null 유지 — 본인 식별이 필요한 버튼(수정)만 숨겨진다.
      });
  }, [farmId]);

  // refreshKey는 작성/수정/삭제 후 재조회를 트리거하는 용도(DiagnosesPageClient와 동일 관례).
  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const res = await listFarmLogs(farmId, page, PAGE_SIZE);
        if (!cancelled) {
          setData(res);
          setLoadError(null);
        }
      } catch (err) {
        if (!cancelled) setLoadError(resolveErrorMessage(err));
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [farmId, page, refreshKey]);

  const isOwner = farm?.myRole === "OWNER";

  async function handleCreate(payload: FarmLogRequest) {
    setFormError(null);
    setSubmitting(true);
    try {
      await createFarmLog(farmId, payload);
      setCreateOpen(false);
      setPage(0);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setFormError(resolveErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleEdit(payload: FarmLogRequest) {
    if (!editTarget) return;
    setFormError(null);
    setSubmitting(true);
    try {
      await updateFarmLog(farmId, editTarget.id, payload);
      setEditTarget(null);
      // logDate가 바뀌면 정렬(내림차순)상 다른 페이지로 옮겨갈 수 있어, 방금 수정한
      // 항목을 바로 찾을 수 있도록 첫 페이지로 이동한다. 날짜가 그대로면 현재 페이지 유지
      // (리뷰 픽스 #57 P3-2).
      if (payload.logDate !== editTarget.logDate) {
        setPage(0);
      }
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setFormError(resolveErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(log: FarmLogResponse) {
    if (!window.confirm("이 작업일지를 삭제하시겠습니까?")) return;
    setRowError(null);
    setBusyId(log.id);
    try {
      await deleteFarmLog(farmId, log.id);
      // 현재 페이지의 마지막 항목을 지우면 같은 page로 재조회 시 빈 화면이 되므로
      // (마지막 페이지가 아니어도 서버가 그 page를 빈 배열로 반환) 한 페이지 되돌린다
      // (리뷰 픽스 #57 P2-1).
      if (data?.content.length === 1 && page > 0) {
        setPage((p) => p - 1);
      } else {
        setRefreshKey((k) => k + 1);
      }
    } catch (err) {
      setRowError(resolveErrorMessage(err));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="flex flex-col gap-4 px-6 py-6">
      <div className="flex items-center justify-between">
        <h2>
          <CardTitle size="lg">작업일지</CardTitle>
        </h2>
        <button
          type="button"
          onClick={() => setCreateOpen(true)}
          className="rounded-md bg-dp-ink px-3 py-1.5 text-sm font-medium text-dp-surface"
        >
          작성
        </button>
      </div>

      {loadError && <p className="text-sm text-dp-red-ink">{loadError}</p>}
      {rowError && <p className="text-sm text-dp-red-ink">{rowError}</p>}

      {!data && !loadError && <p className="text-sm text-dp-muted">불러오는 중...</p>}

      {data && data.content.length === 0 && <p className="text-sm text-dp-muted">작성된 작업일지가 없습니다.</p>}

      {data && data.content.length > 0 && (
        <ul className="flex flex-col gap-2">
          {data.content.map((log) => {
            const isAuthor = myUserId !== null && log.createdBy === myUserId;
            const canEdit = isAuthor;
            const canDelete = isAuthor || isOwner;
            return (
              <li key={log.id}>
                <Card className="flex flex-col gap-1.5 px-4 py-3 text-sm">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-dp-ink">{log.logDate}</span>
                      <StatusBadge label={FARM_LOG_TYPE_LABELS[log.type] ?? log.type} tone="neutral" />
                    </div>
                    {(canEdit || canDelete) && (
                      <div className="flex gap-2 text-xs">
                        {canEdit && (
                          <button
                            type="button"
                            onClick={() => {
                              setFormError(null);
                              setEditTarget(log);
                            }}
                            className="text-dp-sub hover:text-dp-ink hover:underline"
                          >
                            수정
                          </button>
                        )}
                        {canDelete && (
                          <button
                            type="button"
                            disabled={busyId === log.id}
                            onClick={() => handleDelete(log)}
                            className="text-dp-red-ink hover:underline disabled:opacity-60"
                          >
                            삭제
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                  {log.memo && <p className="whitespace-pre-wrap text-dp-sub">{log.memo}</p>}
                </Card>
              </li>
            );
          })}
        </ul>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex items-center gap-3 text-sm">
          <button
            type="button"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="rounded-md border border-dp-line-strong px-2 py-1 text-dp-body disabled:opacity-40"
          >
            이전
          </button>
          <span className="text-dp-muted">
            {data.page + 1} / {data.totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= data.totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-md border border-dp-line-strong px-2 py-1 text-dp-body disabled:opacity-40"
          >
            다음
          </button>
        </div>
      )}

      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="작업일지 작성">
        <FarmLogForm
          submitting={submitting}
          error={formError}
          submitLabel="작성"
          onSubmit={handleCreate}
          onCancel={() => setCreateOpen(false)}
        />
      </Modal>

      <Modal open={editTarget !== null} onClose={() => setEditTarget(null)} title="작업일지 수정">
        {editTarget && (
          <FarmLogForm
            initial={{ logDate: editTarget.logDate, type: editTarget.type, memo: editTarget.memo }}
            submitting={submitting}
            error={formError}
            submitLabel="저장"
            onSubmit={handleEdit}
            onCancel={() => setEditTarget(null)}
          />
        )}
      </Modal>
    </div>
  );
}
