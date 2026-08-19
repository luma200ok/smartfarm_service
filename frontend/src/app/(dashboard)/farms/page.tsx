import type { Metadata } from "next";
import DashboardHeader from "@/components/layout/DashboardHeader";
import FarmCreateForm from "@/components/farms/FarmCreateForm";
import FarmSummaryList from "@/components/farms/FarmSummaryList";

export const metadata: Metadata = {
  title: "농장 목록 | 스마트팜",
};

export default function FarmsPage() {
  return (
    <div className="flex flex-1 flex-col">
      <DashboardHeader title="농장 목록" backHref="/dashboard" />
      <main className="flex flex-col gap-6 px-6 py-6">
        <FarmSummaryList emptyHint="아직 등록된 농장이 없습니다. 아래에서 농장을 만들거나 초대코드로 합류하세요." />
        <FarmCreateForm />
      </main>
    </div>
  );
}
