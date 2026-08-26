"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import PrescriptionStatusCard from "./PrescriptionStatusCard";
import { isForbidden, isNotFound, isTooManyRequests, resolveErrorMessage } from "@/lib/api/errorMessage";
import { getPrescription } from "@/lib/api/prescriptions";
import type { PrescriptionResponse } from "@/types";

interface PrescriptionPollerProps {
  farmId: string;
  prescriptionId: string;
}

const POLL_INTERVAL_MS = 2500;
// P003(429, AI 서버 혼잡) 시 표준 폴링 간격보다 완만하게 백오프한다.
const RATE_LIMIT_RETRY_MS = 5000;
const MAX_RETRIES = 5;
const TERMINAL_STATUSES = new Set(["COMPLETED", "FAILED"]);

// 처방 상세 — PENDING/PROCESSING이면 폴링, 종료 상태(COMPLETED/FAILED)면 멈춘다.
// 일시적 오류(네트워크·5xx·429)는 최대 MAX_RETRIES회까지 재예약하고, 소진되면
// 마지막 상태를 유지한 채 "다시 시도" 버튼으로 사용자가 재개할 수 있게 한다.
// 404/403은 회복 불가한 종료 상태로 취급해 즉시 멈춘다.
export default function PrescriptionPoller({ farmId, prescriptionId }: PrescriptionPollerProps) {
  const [prescription, setPrescription] = useState<PrescriptionResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [forbidden, setForbidden] = useState(false);
  const [retryExhausted, setRetryExhausted] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retryCountRef = useRef(0);
  const [resumeToken, setResumeToken] = useState(0);

  useEffect(() => {
    let cancelled = false;
    retryCountRef.current = 0;

    async function fetchOnce() {
      try {
        const data = await getPrescription(farmId, prescriptionId);
        if (cancelled) return;
        retryCountRef.current = 0;
        setPrescription(data);
        setError(null);
        setRetryExhausted(false);
        if (!TERMINAL_STATUSES.has(data.status)) {
          timerRef.current = setTimeout(fetchOnce, POLL_INTERVAL_MS);
        }
      } catch (err) {
        if (cancelled) return;

        if (isNotFound(err)) {
          setNotFound(true);
          return;
        }
        if (isForbidden(err)) {
          setForbidden(true);
          setError(resolveErrorMessage(err));
          return;
        }

        // 그 외(네트워크 끊김·5xx·429 등 일시적 오류) — 재시도 카운트 소진까지 재예약.
        retryCountRef.current += 1;
        setError(resolveErrorMessage(err));
        if (retryCountRef.current > MAX_RETRIES) {
          setRetryExhausted(true);
          return;
        }
        const delay = isTooManyRequests(err) ? RATE_LIMIT_RETRY_MS : POLL_INTERVAL_MS;
        timerRef.current = setTimeout(fetchOnce, delay);
      }
    }

    fetchOnce();

    return () => {
      cancelled = true;
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [farmId, prescriptionId, resumeToken]);

  function handleRetry() {
    retryCountRef.current = 0;
    setRetryExhausted(false);
    setError(null);
    setResumeToken((t) => t + 1);
  }

  // 페이지 제목(이슈 #136 — 구 FarmTabsHeader 제거로 드릴다운 화면도 제목을 잃었다).
  // 요청 시각은 바로 아래 PrescriptionStatusCard가 이미 표시하므로(리뷰 P3-2) 중복하지
  // 않는다. prescription을 아직 한 번도 못 받아왔으면(retryExhausted 진입 직후 등) 렌더하지
  // 않는다.
  const title = prescription ? (
    <h1 className="text-[17px] leading-[1.2] font-bold text-dp-ink">처방 상세</h1>
  ) : null;

  if (notFound) {
    return (
      <p className="px-6 py-6 text-sm text-dp-sub">
        처방 이력을 찾을 수 없습니다.{" "}
        <Link href={`/farms/${farmId}/prescriptions`} className="underline">
          이력 목록으로
        </Link>
      </p>
    );
  }

  if (forbidden) {
    return <p className="px-6 py-6 text-sm text-dp-red-ink">{error}</p>;
  }

  if (retryExhausted) {
    return (
      <main className="flex flex-col gap-3 px-6 py-6">
        {title}
        {prescription && <PrescriptionStatusCard prescription={prescription} />}
        <p className="text-sm text-dp-red-ink">{error}</p>
        <button
          type="button"
          onClick={handleRetry}
          className="self-start rounded-md border border-dp-line-strong px-3 py-1.5 text-sm text-dp-body"
        >
          다시 시도
        </button>
      </main>
    );
  }

  if (!prescription) {
    return <p className="px-6 py-6 text-sm text-dp-sub">불러오는 중...</p>;
  }

  return (
    <main className="flex flex-col gap-3 px-6 py-6">
      {title}
      <PrescriptionStatusCard prescription={prescription} />
      {error && <p className="text-sm text-dp-amber-deep">일시적인 오류가 발생했습니다. 자동으로 재시도 중입니다...</p>}
    </main>
  );
}
