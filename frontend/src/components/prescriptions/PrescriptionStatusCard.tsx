import { Card, StatusBadge } from "@/components/monitoring/ui";
import { ERROR_MESSAGES } from "@/constants";
import type { PrescriptionResponse } from "@/types";

const STATUS_LABELS: Record<string, string> = {
  PENDING: "대기 중",
  PROCESSING: "처리 중",
  COMPLETED: "완료",
  FAILED: "실패",
};

// 처방 상태 → StatusBadge 톤 (PrescriptionHistoryList와 동일 매핑, 이슈 #109).
function statusTone(status: string): "done" | "warning" | "neutral" | "critical" {
  switch (status) {
    case "COMPLETED":
      return "done";
    case "FAILED":
      return "critical";
    default:
      return "neutral";
  }
}

interface PrescriptionStatusCardProps {
  prescription: PrescriptionResponse;
}

// 처방 상태별 렌더 — PENDING/PROCESSING 진행 표시, COMPLETED 결과, FAILED 안내.
// 상세 화면(폴링)과 이력 상세 조회에서 공용으로 쓴다.
// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card·StatusBadge)로 통일한다(이슈 #109).
export default function PrescriptionStatusCard({ prescription }: PrescriptionStatusCardProps) {
  return (
    <Card className="flex flex-col gap-3 p-4">
      <div className="flex items-center justify-between">
        <StatusBadge label={STATUS_LABELS[prescription.status] ?? prescription.status} tone={statusTone(prescription.status)} />
        <span className="text-xs text-dp-faint">{new Date(prescription.createdAt).toLocaleString()}</span>
      </div>

      <p className="text-sm text-dp-ink">{prescription.question}</p>

      {(prescription.status === "PENDING" || prescription.status === "PROCESSING") && (
        <p className="text-sm text-dp-sub">AI가 처방을 준비하고 있습니다. 잠시만 기다려주세요...</p>
      )}

      {prescription.status === "FAILED" && (
        <p className="text-sm text-dp-red-ink">
          {(prescription.errorCode && ERROR_MESSAGES[prescription.errorCode]) ||
            "처방 생성에 실패했습니다. 잠시 후 다시 시도해주세요."}
        </p>
      )}

      {prescription.status === "COMPLETED" && prescription.result && (
        <div className="flex flex-col gap-3 border-t border-dp-line pt-3">
          <p className="text-sm text-dp-body">{prescription.result.summary}</p>
          {prescription.result.actions.length > 0 && (
            <div>
              <h4 className="text-xs font-semibold text-dp-sub">조치 사항</h4>
              <ul className="mt-1 list-inside list-disc text-sm text-dp-body">
                {prescription.result.actions.map((action, i) => (
                  <li key={i}>{action}</li>
                ))}
              </ul>
            </div>
          )}
          {prescription.result.caution && (
            <p className="whitespace-pre-line rounded-md bg-dp-amber-tint px-3 py-2 text-sm text-dp-amber-deep">
              ⚠ {prescription.result.caution}
            </p>
          )}
          {prescription.result.sources.length > 0 && (
            <div>
              <h4 className="text-xs font-semibold text-dp-sub">참고 자료</h4>
              <ul className="mt-1 list-inside list-disc text-xs text-dp-muted">
                {prescription.result.sources.map((source, i) => (
                  <li key={i}>{source}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </Card>
  );
}
