"use client";

import { useEffect, useState, type FormEvent } from "react";
import { VALIDATION } from "@/constants";
import { isTooManyRequests, resolveErrorMessage } from "@/lib/api/errorMessage";
import { listChatMessages, sendChatMessage } from "@/lib/api/chat";
import { getFarm } from "@/lib/api/farms";
import { hasFarmRoleAtLeast } from "@/lib/roles";
import type { ChatMessageResponse, FarmResponse, PageResponse } from "@/types";

interface FarmChatProps {
  farmId: string;
}

const PAGE_SIZE = 20;

// AI 상담 탭(이슈 #54·#55, contract §4.7 → #144 시안 05 적용) — 이력 목록 + 입력창을 한
// 컴포넌트에서 오케스트레이션(FarmLogList·FarmMembers와 동일한 "탭 하나 = 컴포넌트 하나" 관례).
// 날씨·농약 카드는 형제 컴포넌트(ForecastWidget·PesticideCard)로 옆에 배치한다(chat/page.tsx).
//
// ⚠️ 보안 필수(contract §4.7): answer·sources는 LLM이 생성한 자유 텍스트이고 이력으로 재노출된다.
// React 기본 이스케이프(텍스트 노드)로만 렌더한다 — dangerouslySetInnerHTML·raw HTML 마크다운
// 렌더러 사용 절대 금지(저장형 XSS). 줄바꿈은 CSS white-space: pre-wrap으로만 처리한다.
//
// ⚠️ sources는 string[](근거 출처 문자열)이지 차트 데이터가 아니다(메타 실측, 이슈 #144 handoff)
// — 시안의 AI 버블 안 인라인 차트(SVG)는 그릴 값이 없어 렌더하지 않고, 대신 텍스트 목록으로
// 표시한다(#128 농약 출처·#129 harvestDueSoon과 동일 원칙 — 없는 데이터를 지어내지 않는다).
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
  const [farm, setFarm] = useState<FarmResponse | null>(null);

  // 채팅 전송은 OPERATOR 이상(contract §2, 이슈 #122/#123 리뷰 P2-B) — 이력 조회는 requireMember라
  // VIEWER도 볼 수 있으니 목록은 그대로 두고 입력창만 안내 문구로 대체한다. 조회 실패해도
  // 조용히 canWrite=false로 남긴다(보수적으로 숨김).
  useEffect(() => {
    let cancelled = false;
    getFarm(farmId)
      .then((res) => {
        if (!cancelled) setFarm(res);
      })
      .catch(() => {
        // no-op
      });
    return () => {
      cancelled = true;
    };
  }, [farmId]);

  const canWrite = hasFarmRoleAtLeast(farm?.myRole, "OPERATOR");

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
    <div className="flex flex-col">
      <div className="flex flex-none items-center gap-2.5 border-b border-dp-line px-[18px] py-3.5">
        <span className="flex h-[26px] w-[26px] flex-none items-center justify-center rounded-[7px] bg-dp-green text-[11px] font-bold text-dp-on-green">
          AI
        </span>
        <span className="text-sm font-semibold text-dp-ink">스마트팜 어시스턴트</span>
        {farm && <span className="text-[11.5px] text-dp-muted">{farm.name} 연결됨</span>}
        <div className="flex-1" />
        {data && data.totalPages > 1 && (
          <div className="flex items-center gap-2 text-xs">
            <button
              type="button"
              disabled={page <= 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="rounded-md border border-dp-line-strong px-2 py-1 text-dp-body disabled:opacity-40"
            >
              최신
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
              이전 대화
            </button>
          </div>
        )}
      </div>

      <div className="max-h-[520px] overflow-y-auto px-[18px] py-[18px]">
        {loadError && <p className="text-sm text-dp-red-ink">{loadError}</p>}

        {!data && !loadError && <p className="text-sm text-dp-sub">불러오는 중...</p>}

        {data && data.content.length === 0 && (
          <p className="text-sm text-dp-sub">아직 대화가 없습니다. 병해·재배 환경에 대해 물어보세요.</p>
        )}

        {data && orderedMessages.length > 0 && (
          <ul className="flex flex-col gap-3.5">
            {orderedMessages.map((msg) => (
              <li key={msg.id} className="flex flex-col gap-2">
                <div className="max-w-[62%] self-end rounded-[12px_12px_3px_12px] bg-dp-green-tint-2 px-[15px] py-3 text-[13px] leading-[1.6] text-dp-ink">
                  <p className="whitespace-pre-wrap">{msg.question}</p>
                </div>
                <div className="max-w-[76%] self-start rounded-[12px_12px_12px_3px] bg-dp-inset px-4 py-[13px] text-[13px] leading-[1.65] text-dp-ink">
                  {msg.fallback && (
                    <p className="mb-2 rounded bg-dp-amber-tint px-2 py-1 text-xs text-dp-amber-deep">
                      AI가 답변을 생성하지 못했습니다.
                    </p>
                  )}
                  <p className="whitespace-pre-wrap">{msg.answer}</p>
                  {/* sources는 근거 출처 문자열 목록이지 차트 데이터가 아니다(handoff 실측) — 텍스트로만 표시. */}
                  {msg.sources.length > 0 && (
                    <details className="mt-2.5 text-xs text-dp-muted">
                      <summary className="cursor-pointer select-none font-medium">근거 자료 ({msg.sources.length})</summary>
                      <ul className="mt-1 list-inside list-disc">
                        {msg.sources.map((source, i) => (
                          <li key={i}>{source}</li>
                        ))}
                      </ul>
                    </details>
                  )}
                  <span className="mt-1.5 block text-[11px] text-dp-faint">
                    {new Date(msg.createdAt).toLocaleString()}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {canWrite ? (
        <form onSubmit={handleSubmit} className="flex flex-none flex-col gap-2 border-t border-dp-line px-[18px] py-3.5">
          <div className="flex items-center gap-2.5">
            <label htmlFor="chat-question" className="sr-only">
              질문
            </label>
            <textarea
              id="chat-question"
              rows={1}
              required
              maxLength={VALIDATION.chatQuestion.maxLength}
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              placeholder="농장 상태나 데이터에 대해 물어보세요"
              className="flex-1 resize-none rounded-[9px] bg-dp-inset px-3.5 py-3 text-[13px] text-dp-ink placeholder:text-dp-faint"
            />
            <button
              type="submit"
              disabled={submitting || question.trim().length === 0}
              className="flex-none rounded-[9px] bg-dp-green px-[18px] py-3 text-[13px] font-semibold text-dp-on-green disabled:opacity-40"
            >
              {submitting ? "전송 중..." : "전송"}
            </button>
          </div>
          <span className="text-xs text-dp-faint">
            {question.length} / {VALIDATION.chatQuestion.maxLength}자 (남은 {Math.max(0, remaining)}자)
          </span>

          {retryHint && (
            <p className="rounded-md border border-dp-amber-line bg-dp-amber-tint px-3 py-2 text-sm text-dp-amber-deep">
              {retryHint}
            </p>
          )}
          {sendError && <p className="text-sm text-dp-red-ink">{sendError}</p>}
        </form>
      ) : (
        <p className="flex-none border-t border-dp-line px-[18px] py-3.5 text-sm text-dp-faint">조회 전용 역할입니다.</p>
      )}
    </div>
  );
}
