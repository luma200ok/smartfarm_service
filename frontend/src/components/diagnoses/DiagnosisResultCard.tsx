import { Card, CardTitle } from "@/components/monitoring/ui";
import type { DiagnosisResponse } from "@/types";

interface DiagnosisResultCardProps {
  diagnosis: DiagnosisResponse;
}

// 진단 결과 렌더 — 업로드 직후 즉시 결과 표시와 상세 화면에서 공용으로 쓴다.
// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card·CardTitle)로 통일한다(이슈 #109).
export default function DiagnosisResultCard({ diagnosis }: DiagnosisResultCardProps) {
  if (diagnosis.status === "ood_blocked") {
    return (
      <Card className="!border-dp-amber-line !bg-dp-amber-tint p-4 text-sm text-dp-amber-deep">
        <p className="font-semibold">이미지를 진단할 수 없습니다.</p>
        <p className="mt-1">{diagnosis.reason ?? "농작물 이미지로 인식되지 않았습니다."}</p>
      </Card>
    );
  }

  return (
    <Card className="flex flex-col gap-3 p-4">
      <div className="flex items-center justify-between">
        <CardTitle as="h3" size="lg">{diagnosis.labelKr}</CardTitle>
        <span className="rounded bg-dp-badge-neutral px-2 py-0.5 text-xs text-dp-sub">
          확신도 {(diagnosis.prob * 100).toFixed(1)}%
        </span>
      </div>
      <p className="text-sm text-dp-sub">부위: {diagnosis.part}</p>
      {diagnosis.camPngBase64 && (
        <img
          src={`data:image/png;base64,${diagnosis.camPngBase64}`}
          alt="진단 히트맵"
          className="max-w-full rounded-md border border-dp-line"
        />
      )}
      <p className="text-xs text-dp-faint">{new Date(diagnosis.createdAt).toLocaleString()}</p>
    </Card>
  );
}
