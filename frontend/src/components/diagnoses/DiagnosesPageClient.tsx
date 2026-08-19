"use client";

import { useState } from "react";
import DiagnosisHistoryList from "./DiagnosisHistoryList";
import DiagnosisUploadForm from "./DiagnosisUploadForm";

interface DiagnosesPageClientProps {
  farmId: string;
}

// 업로드 성공 시 이력 목록을 자동 새로고침하기 위한 클라이언트 오케스트레이터.
export default function DiagnosesPageClient({ farmId }: DiagnosesPageClientProps) {
  const [refreshKey, setRefreshKey] = useState(0);

  return (
    <main className="flex flex-col gap-8 px-6 py-6">
      <DiagnosisUploadForm farmId={farmId} onUploaded={() => setRefreshKey((k) => k + 1)} />
      <DiagnosisHistoryList farmId={farmId} refreshKey={refreshKey} />
    </main>
  );
}
