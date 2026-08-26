import type { Metadata } from "next";
import FarmsPageClient from "@/components/farms/FarmsPageClient";

export const metadata: Metadata = {
  title: "농장 목록 | 스마트팜",
};

// farm 스코프 밖 화면(이슈 #133 IA) — 좌측 내비 대상이 아니라 GlobalBar 농장 드롭다운의
// "농장 목록 전체 보기"로 도달한다. 구 DashboardHeader의 title/backHref는 GlobalBar가
// 상시 노출하는 로고·탭으로 대체돼 더 이상 필요 없다.
export default function FarmsPage() {
  return (
    <div className="flex flex-1 flex-col">
      <h1 className="px-6 pt-6 text-lg font-semibold text-dp-ink">농장 목록</h1>
      <FarmsPageClient />
    </div>
  );
}
