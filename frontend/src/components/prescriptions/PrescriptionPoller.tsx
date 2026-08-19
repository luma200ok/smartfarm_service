"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import PrescriptionStatusCard from "./PrescriptionStatusCard";
import { isNotFound, resolveErrorMessage } from "@/lib/api/errorMessage";
import { getPrescription } from "@/lib/api/prescriptions";
import type { PrescriptionResponse } from "@/types";

interface PrescriptionPollerProps {
  farmId: string;
  prescriptionId: string;
}

const POLL_INTERVAL_MS = 2500;
const TERMINAL_STATUSES = new Set(["COMPLETED", "FAILED"]);

// 처방 상세 — PENDING/PROCESSING이면 2.5초 간격으로 폴링, 종료 상태(COMPLETED/FAILED)면 멈춘다.
export default function PrescriptionPoller({ farmId, prescriptionId }: PrescriptionPollerProps) {
  const [prescription, setPrescription] = useState<PrescriptionResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function fetchOnce() {
      try {
        const data = await getPrescription(farmId, prescriptionId);
        if (cancelled) return;
        setPrescription(data);
        setError(null);
        if (!TERMINAL_STATUSES.has(data.status)) {
          timerRef.current = setTimeout(fetchOnce, POLL_INTERVAL_MS);
        }
      } catch (err) {
        if (cancelled) return;
        if (isNotFound(err)) {
          setNotFound(true);
        } else {
          setError(resolveErrorMessage(err));
        }
      }
    }

    fetchOnce();

    return () => {
      cancelled = true;
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [farmId, prescriptionId]);

  if (notFound) {
    return (
      <p className="px-6 py-6 text-sm text-zinc-500 dark:text-zinc-400">
        처방 이력을 찾을 수 없습니다.{" "}
        <Link href={`/farms/${farmId}/prescriptions`} className="underline">
          이력 목록으로
        </Link>
      </p>
    );
  }

  if (error && !prescription) {
    return <p className="px-6 py-6 text-sm text-red-600 dark:text-red-400">{error}</p>;
  }

  if (!prescription) {
    return <p className="px-6 py-6 text-sm text-zinc-500 dark:text-zinc-400">불러오는 중...</p>;
  }

  return (
    <main className="flex flex-col gap-3 px-6 py-6">
      <PrescriptionStatusCard prescription={prescription} />
      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
    </main>
  );
}
