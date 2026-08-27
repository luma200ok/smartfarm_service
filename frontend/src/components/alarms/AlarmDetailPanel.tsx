"use client";

import type { ReactNode } from "react";
import { useState } from "react";
import Link from "next/link";
import { ALARM_STATUS_LABELS, ALARM_TIMELINE_ACTION_LABELS } from "@/constants";
import { Card, CardTitle } from "@/components/monitoring/ui";
import Modal from "@/components/ui/Modal";
import {
  alarmDisplayTone,
  alarmToneTextClass,
  formatAlarmDateTime,
  formatAlarmTimestamp,
} from "@/lib/alarmDisplay";
import type { AlarmEventDetailResponse } from "@/types";

interface AlarmDetailPanelProps {
  farmId: string;
  detail: AlarmEventDetailResponse | null;
  loading: boolean;
  error: string | null;
  location: string;
  ruleSummary: string | null;
  ruleLoading: boolean;
  canWrite: boolean;
  actionBusy: boolean;
  actionError: string | null;
  onAcknowledge: () => void;
  onResolve: () => void;
  memoOpen: boolean;
  memoSubmitting: boolean;
  memoError: string | null;
  onMemoOpen: () => void;
  onMemoClose: () => void;
  onMemoSubmit: (note: string) => void;
  /** 터치 타깃을 44px로 키운다(이슈 #147 리뷰 — 모바일 알람 상세 모달 전용). 기본 false라
   * 이 prop을 넘기지 않는 기존 호출부(데스크톱 우측 패널)는 마크업·크기가 완전히 그대로다 —
   * 이 컴포넌트는 데스크톱·모바일 양쪽에서 재사용되는데, 원래 마우스 클릭 기준(py-2.5 ≈ 32px)으로
   * 만들어져 있어 모바일 Modal에 그대로 얹으면 44px 미달이 된다. ThemeToggle.tsx의 padded prop과
   * 같은 원칙으로 opt-in 시켜 데스크톱 회귀를 차단한다. */
  touchPadded?: boolean;
}

// 우측 상세 패널(시안 04 · 이슈 #136) — 상단 알람 정보 카드 + 하단 처리 이력 카드.
// "현재값"·"추정 원인" 블록은 대응 API가 없어 렌더하지 않는다(핸드오프 "없는 데이터를 지어내지
// 말 것" — #128 농약 출처 표기, #129 harvestDueSoon 필드 미생성과 같은 원칙).
export default function AlarmDetailPanel({
  farmId,
  detail,
  loading,
  error,
  location,
  ruleSummary,
  ruleLoading,
  canWrite,
  actionBusy,
  actionError,
  onAcknowledge,
  onResolve,
  memoOpen,
  memoSubmitting,
  memoError,
  onMemoOpen,
  onMemoClose,
  onMemoSubmit,
  touchPadded = false,
}: AlarmDetailPanelProps) {
  const touchClass = touchPadded
    ? " min-h-11 flex items-center justify-center"
    : "";
  if (loading) {
    return (
      <Card className="px-4 py-[15px]">
        <p className="text-[12.5px] text-dp-muted">불러오는 중...</p>
      </Card>
    );
  }

  if (error) {
    return (
      <Card className="px-4 py-[15px]">
        <p className="text-[12.5px] text-dp-red-ink">{error}</p>
      </Card>
    );
  }

  if (!detail) {
    return (
      <Card className="px-4 py-[15px]">
        <p className="text-[12.5px] text-dp-muted">
          왼쪽 목록에서 알람을 선택하면 상세 정보가 표시됩니다.
        </p>
      </Card>
    );
  }

  const { event, timeline } = detail;
  const tone = alarmDisplayTone(event.severity, event.status);
  const severityLabel =
    tone === "done" ? "완료" : event.severity === "CRITICAL" ? "경보" : "주의";

  const canAcknowledge = canWrite && event.status === "UNACKNOWLEDGED";
  const canResolve = canWrite && event.status === "ACKNOWLEDGED";

  return (
    <div className="flex flex-col gap-3">
      <Card className="px-4 py-[15px]">
        <div className="mb-3 flex items-baseline justify-between gap-2">
          <CardTitle as="h2">{event.message}</CardTitle>
          <span
            className={`flex-none font-mono text-[10.5px] leading-none font-semibold ${alarmToneTextClass(tone)}`}
          >
            {severityLabel}
          </span>
        </div>

        <dl className="flex flex-col gap-[7px] text-[12px] leading-[1.5] text-dp-body">
          <Field label="위치">{location}</Field>
          {ruleLoading && <Field label="규칙">불러오는 중...</Field>}
          {!ruleLoading && ruleSummary && (
            <Field label="규칙">{ruleSummary}</Field>
          )}
          <Field label="발생">{formatAlarmDateTime(event.occurredAt)}</Field>
          <Field label="상태">{ALARM_STATUS_LABELS[event.status]}</Field>
        </dl>

        {actionError && (
          <p className="mt-2 text-[11.5px] text-dp-red-ink">{actionError}</p>
        )}

        {canWrite && (
          <div className="mt-3 flex gap-2">
            <button
              type="button"
              onClick={onAcknowledge}
              disabled={!canAcknowledge || actionBusy}
              className={`flex-1 rounded-[7px] border py-2.5 text-center text-[12.5px] leading-none font-semibold ${
                !canAcknowledge || actionBusy
                  ? "border-dp-line text-dp-faint"
                  : "border-dp-line-strong text-dp-body hover:bg-dp-inset"
              }${touchClass}`}
            >
              {event.status === "UNACKNOWLEDGED" ? "확인" : "확인됨"}
            </button>
            <button
              type="button"
              onClick={onResolve}
              disabled={!canResolve || actionBusy}
              className={`flex-1 rounded-[7px] border py-2.5 text-center text-[12.5px] leading-none font-semibold ${
                !canResolve || actionBusy
                  ? "border-dp-line text-dp-faint"
                  : "border-dp-line-strong text-dp-body hover:bg-dp-inset"
              }${touchClass}`}
            >
              {event.status === "RESOLVED" ? "처리 완료됨" : "처리 완료"}
            </button>
          </div>
        )}

        <Link
          href={`/farms/${farmId}/control`}
          className={`mt-2 block rounded-[7px] bg-dp-green py-2.5 text-center text-[12.5px] leading-none font-semibold text-dp-on-green${touchClass}`}
        >
          제어 화면 이동
        </Link>
      </Card>

      <Card className="flex flex-1 flex-col px-4 py-[15px]">
        <div className="mb-2.5">
          <CardTitle>처리 이력</CardTitle>
        </div>
        <div>
          {timeline.map((entry, i) => (
            <TimelineRow
              key={entry.id}
              time={formatAlarmTimestamp(entry.createdAt)}
              text={
                entry.action === "MEMO_ADDED" && entry.note
                  ? `${ALARM_TIMELINE_ACTION_LABELS[entry.action]}: ${entry.note}`
                  : ALARM_TIMELINE_ACTION_LABELS[entry.action]
              }
              last={i === timeline.length - 1}
            />
          ))}
        </div>

        {canWrite && (
          <button
            type="button"
            onClick={onMemoOpen}
            className={`mt-2.5 rounded-[7px] border border-dp-line-strong py-2.5 text-center text-[12.5px] leading-none font-semibold text-dp-body hover:bg-dp-inset${touchClass}`}
          >
            메모 남기기
          </button>
        )}
      </Card>

      <MemoModal
        open={memoOpen}
        submitting={memoSubmitting}
        error={memoError}
        onClose={onMemoClose}
        onSubmit={onMemoSubmit}
        touchPadded={touchPadded}
      />
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex gap-2.5">
      <dt className="w-14 flex-none text-dp-muted">{label}</dt>
      <dd className="min-w-0">{children}</dd>
    </div>
  );
}

function TimelineRow({
  time,
  text,
  last,
}: {
  time: string;
  text: string;
  last: boolean;
}) {
  return (
    <div
      className={`flex gap-2.5 py-[7px] ${last ? "" : "border-b border-dp-line-row"}`}
    >
      <span className="w-[42px] flex-none font-mono text-[11px] leading-[1.4] font-medium text-dp-muted">
        {time}
      </span>
      <span className="flex-1 text-[12px] leading-[1.4] font-medium text-dp-ink">
        {text}
      </span>
    </div>
  );
}

function MemoModal({
  open,
  submitting,
  error,
  onClose,
  onSubmit,
  touchPadded = false,
}: {
  open: boolean;
  submitting: boolean;
  error: string | null;
  onClose: () => void;
  onSubmit: (note: string) => void;
  touchPadded?: boolean;
}) {
  const touchClass = touchPadded
    ? " min-h-11 flex items-center justify-center"
    : "";
  const [note, setNote] = useState("");
  // open이 false로 바뀌면(취소·backdrop·성공 후 부모의 닫기 모두 포함) 다음에 열 때를 위해
  // 입력을 비운다 — AppShell의 drawerClosedFor와 동일한 "prop 변화에 상태 동기화" 렌더 중 조정 패턴.
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (!open) setNote("");
  }

  return (
    <Modal open={open} onClose={onClose} title="메모 남기기">
      <form
        className="flex flex-col gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          if (!note.trim()) return;
          onSubmit(note.trim());
        }}
      >
        <textarea
          value={note}
          onChange={(e) => setNote(e.target.value)}
          maxLength={1000}
          rows={4}
          placeholder="처리 내용이나 참고 사항을 남겨주세요."
          className="rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-900 outline-none focus:border-zinc-500 focus:ring-1 focus:ring-zinc-500 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50"
        />
        {error && (
          <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
        )}
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className={`rounded-md border border-zinc-300 px-3 py-1.5 text-sm text-zinc-700 dark:border-zinc-700 dark:text-zinc-300${touchClass}`}
          >
            취소
          </button>
          <button
            type="submit"
            disabled={submitting || !note.trim()}
            className={`rounded-md bg-dp-ink px-3 py-1.5 text-sm font-medium text-dp-surface disabled:opacity-60${touchClass}`}
          >
            {submitting ? "저장 중..." : "저장"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
