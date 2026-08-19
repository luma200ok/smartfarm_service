import { ERROR_MESSAGES } from "@/constants";
import type { PrescriptionResponse } from "@/types";

const STATUS_LABELS: Record<string, string> = {
  PENDING: "대기 중",
  PROCESSING: "처리 중",
  COMPLETED: "완료",
  FAILED: "실패",
};

interface PrescriptionStatusCardProps {
  prescription: PrescriptionResponse;
}

// 처방 상태별 렌더 — PENDING/PROCESSING 진행 표시, COMPLETED 결과, FAILED 안내.
// 상세 화면(폴링)과 이력 상세 조회에서 공용으로 쓴다.
export default function PrescriptionStatusCard({ prescription }: PrescriptionStatusCardProps) {
  return (
    <div className="flex flex-col gap-3 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
      <div className="flex items-center justify-between">
        <span className="rounded bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">
          {STATUS_LABELS[prescription.status] ?? prescription.status}
        </span>
        <span className="text-xs text-zinc-400 dark:text-zinc-500">
          {new Date(prescription.createdAt).toLocaleString()}
        </span>
      </div>

      <p className="text-sm text-zinc-900 dark:text-zinc-50">{prescription.question}</p>

      {(prescription.status === "PENDING" || prescription.status === "PROCESSING") && (
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          AI가 처방을 준비하고 있습니다. 잠시만 기다려주세요...
        </p>
      )}

      {prescription.status === "FAILED" && (
        <p className="text-sm text-red-600 dark:text-red-400">
          {(prescription.errorCode && ERROR_MESSAGES[prescription.errorCode]) ||
            "처방 생성에 실패했습니다. 잠시 후 다시 시도해주세요."}
        </p>
      )}

      {prescription.status === "COMPLETED" && prescription.result && (
        <div className="flex flex-col gap-3 border-t border-zinc-100 pt-3 dark:border-zinc-800">
          <p className="text-sm text-zinc-700 dark:text-zinc-300">{prescription.result.summary}</p>
          {prescription.result.actions.length > 0 && (
            <div>
              <h4 className="text-xs font-semibold text-zinc-500 dark:text-zinc-400">조치 사항</h4>
              <ul className="mt-1 list-inside list-disc text-sm text-zinc-700 dark:text-zinc-300">
                {prescription.result.actions.map((action, i) => (
                  <li key={i}>{action}</li>
                ))}
              </ul>
            </div>
          )}
          {prescription.result.caution && (
            <p className="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:bg-amber-950 dark:text-amber-200">
              ⚠ {prescription.result.caution}
            </p>
          )}
          {prescription.result.sources.length > 0 && (
            <div>
              <h4 className="text-xs font-semibold text-zinc-500 dark:text-zinc-400">참고 자료</h4>
              <ul className="mt-1 list-inside list-disc text-xs text-zinc-500 dark:text-zinc-400">
                {prescription.result.sources.map((source, i) => (
                  <li key={i}>{source}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
