import type { DiagnosisResponse } from "@/types";

interface DiagnosisResultCardProps {
  diagnosis: DiagnosisResponse;
}

// 진단 결과 렌더 — 업로드 직후 즉시 결과 표시와 상세 화면에서 공용으로 쓴다.
export default function DiagnosisResultCard({ diagnosis }: DiagnosisResultCardProps) {
  if (diagnosis.status === "ood_blocked") {
    return (
      <div className="rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200">
        <p className="font-semibold">이미지를 진단할 수 없습니다.</p>
        <p className="mt-1">{diagnosis.reason ?? "농작물 이미지로 인식되지 않았습니다."}</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">{diagnosis.labelKr}</h3>
        <span className="rounded bg-zinc-100 px-2 py-0.5 text-xs text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">
          확신도 {(diagnosis.prob * 100).toFixed(1)}%
        </span>
      </div>
      <p className="text-sm text-zinc-500 dark:text-zinc-400">부위: {diagnosis.part}</p>
      {diagnosis.camPngBase64 && (
        <img
          src={`data:image/png;base64,${diagnosis.camPngBase64}`}
          alt="진단 히트맵"
          className="max-w-full rounded-md border border-zinc-200 dark:border-zinc-800"
        />
      )}
      <p className="text-xs text-zinc-400 dark:text-zinc-500">
        {new Date(diagnosis.createdAt).toLocaleString()}
      </p>
    </div>
  );
}
