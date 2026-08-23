"use client";

import { useEffect, useState, type FormEvent } from "react";
import { VALIDATION } from "@/constants";
import { isTooManyRequests, resolveErrorMessage } from "@/lib/api/errorMessage";
import { listChatMessages, sendChatMessage } from "@/lib/api/chat";
import type { ChatMessageResponse, PageResponse } from "@/types";

interface FarmChatProps {
  farmId: string;
}

const PAGE_SIZE = 20;

// AI 상담 탭(이슈 #54·#55, contract §4.7) — 이력 목록 + 입력창을 한 컴포넌트에서 오케스트레이션
// (FarmLogList·FarmMembers와 동일한 "탭 하나 = 컴포넌트 하나" 관례).
//
// ⚠️ 보안 필수(contract §4.7): answer·sources는 LLM이 생성한 자유 텍스트이고 이력으로 재노출된다.
// React 기본 이스케이프(텍스트 노드)로만 렌더한다 — dangerouslySetInnerHTML·raw HTML 마크다운
// 렌더러 사용 절대 금지(저장형 XSS). 줄바꿈은 CSS white-space: pre-wrap으로만 처리한다.
export default function FarmChat({ farmId }: FarmChatProps) {
  // GET .../chat은 최신순 페이지를 반환한다(FarmLogList와 동일한 page0=최신 관례).
  // page0에서 새 메시지가 항상 보이도록, 전송 성공 시 page를 0으로 되돌린다.
  const [page, setPage] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);
  const [data, setData] = useState<PageResponse<ChatMessageResponse> | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [question, setQuestion] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  // CH002(429, AI 서버 혼잡)는 실패가 아니라 재시도 유도 안내라 별도 스타일로 렌더한다
  // (PrescriptionCreateForm의 limitExceeded와 동일 관례). status 기반 분기 — 문자열 매칭 금지.
  const [retryHint, setRetryHint] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const res = await listChatMessages(farmId, page, PAGE_SIZE);
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

  // 서버는 최신순으로 내려주므로, 한 페이지 안에서는 시간순(오래된→최신)으로 뒤집어 보여준다.
  const orderedMessages = data ? [...data.content].reverse() : [];

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (submitting) return; // 중복 전송 방지

    const trimmed = question.trim();
    // 최소/최대를 나눠 안내한다 — 합쳐 두면 빈 입력에도 "500자 이하" 안내가 나가 원인과 반대로 읽힌다(리뷰 P2).
    if (trimmed.length < VALIDATION.chatQuestion.minLength) {
      setSendError("질문을 입력해주세요.");
      return;
    }
    if (trimmed.length > VALIDATION.chatQuestion.maxLength) {
      setSendError(`질문은 ${VALIDATION.chatQuestion.maxLength}자 이하로 입력해주세요.`);
      return;
    }

    setSendError(null);
    setRetryHint(null);
    setSubmitting(true);
    try {
      await sendChatMessage(farmId, { question: trimmed });
      setQuestion("");
      setPage(0);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      if (isTooManyRequests(err)) {
        setRetryHint(resolveErrorMessage(err));
      } else {
        setSendError(resolveErrorMessage(err));
      }
    } finally {
      setSubmitting(false);
    }
  }

  const remaining = VALIDATION.chatQuestion.maxLength - question.length;

  return (
    <div className="flex flex-col gap-4 px-6 py-6">
      <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">AI 상담</h2>

      {loadError && <p className="text-sm text-red-600 dark:text-red-400">{loadError}</p>}

      {!data && !loadError && <p className="text-sm text-zinc-500 dark:text-zinc-400">불러오는 중...</p>}

      {data && data.content.length === 0 && (
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          아직 대화가 없습니다. 병해·재배 환경에 대해 물어보세요.
        </p>
      )}

      {data && orderedMessages.length > 0 && (
        <ul className="flex flex-col gap-4">
          {orderedMessages.map((msg) => (
            <li key={msg.id} className="flex flex-col gap-2">
              <div className="max-w-[85%] self-end rounded-lg bg-zinc-900 px-3 py-2 text-sm text-white dark:bg-zinc-50 dark:text-zinc-900">
                <p className="whitespace-pre-wrap">{msg.question}</p>
              </div>
              <div className="flex max-w-[85%] flex-col gap-1.5 self-start rounded-lg border border-zinc-200 px-3 py-2 text-sm dark:border-zinc-800">
                {msg.fallback && (
                  <p className="rounded bg-amber-50 px-2 py-1 text-xs text-amber-800 dark:bg-amber-950 dark:text-amber-200">
                    AI가 답변을 생성하지 못했습니다.
                  </p>
                )}
                <p className="whitespace-pre-wrap text-zinc-700 dark:text-zinc-300">{msg.answer}</p>
                {msg.sources.length > 0 && (
                  <details className="text-xs text-zinc-500 dark:text-zinc-400">
                    <summary className="cursor-pointer select-none">근거 자료 ({msg.sources.length})</summary>
                    <ul className="mt-1 list-inside list-disc">
                      {msg.sources.map((source, i) => (
                        <li key={i}>{source}</li>
                      ))}
                    </ul>
                  </details>
                )}
                <span className="text-[11px] text-zinc-400 dark:text-zinc-500">
                  {new Date(msg.createdAt).toLocaleString()}
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex items-center gap-3 text-sm">
          <button
            type="button"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="rounded-md border border-zinc-300 px-2 py-1 disabled:opacity-40 dark:border-zinc-700"
          >
            최신
          </button>
          <span className="text-zinc-500 dark:text-zinc-400">
            {data.page + 1} / {data.totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= data.totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-md border border-zinc-300 px-2 py-1 disabled:opacity-40 dark:border-zinc-700"
          >
            이전 대화
          </button>
        </div>
      )}

      <form
        onSubmit={handleSubmit}
        className="flex flex-col gap-2 border-t border-zinc-200 pt-4 dark:border-zinc-800"
      >
        <label htmlFor="chat-question" className="sr-only">
          질문
        </label>
        <textarea
          id="chat-question"
          rows={3}
          required
          maxLength={VALIDATION.chatQuestion.maxLength}
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="병해·재배 환경에 대해 물어보세요"
          className="rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-900 outline-none focus:border-zinc-500 focus:ring-1 focus:ring-zinc-500 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50"
        />
        <div className="flex items-center justify-between">
          <span className="text-xs text-zinc-400 dark:text-zinc-500">
            {question.length} / {VALIDATION.chatQuestion.maxLength}자 (남은 {Math.max(0, remaining)}자)
          </span>
          <button
            type="submit"
            disabled={submitting || question.trim().length === 0}
            className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            {submitting ? "전송 중..." : "전송"}
          </button>
        </div>

        {retryHint && (
          <p className="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:bg-amber-950 dark:text-amber-200">
            {retryHint}
          </p>
        )}
        {sendError && <p className="text-sm text-red-600 dark:text-red-400">{sendError}</p>}
      </form>
    </div>
  );
}
