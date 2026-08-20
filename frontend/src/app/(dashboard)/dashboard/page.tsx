import type { Metadata } from "next";
import DashboardHome from "@/components/dashboard/DashboardHome";

export const metadata: Metadata = {
  title: "대시보드 | 스마트팜",
};

// 대시보드 홈 — 내 농장 목록 요약 + 환경 위젯. 농장이 없으면 생성/합류로 유도한다. (이슈 #6, #22)
export default function DashboardPage() {
  return <DashboardHome />;
}
