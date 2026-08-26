import type { Metadata } from "next";
import PrescriptionCreateForm from "@/components/prescriptions/PrescriptionCreateForm";
import PrescriptionHistoryList from "@/components/prescriptions/PrescriptionHistoryList";

export const metadata: Metadata = {
  title: "처방 | 스마트팜",
};

export default async function FarmPrescriptionsPage(
  props: PageProps<"/farms/[farmId]/prescriptions">
) {
  const { farmId } = await props.params;
  return (
    <main className="flex flex-col gap-8 px-6 py-6">
      {/* 페이지 제목(이슈 #136 — 구 FarmTabsHeader 제거로 이 화면엔 제목이 없었다). */}
      <h1 className="text-[17px] leading-[1.2] font-bold text-dp-ink">처방</h1>
      <PrescriptionCreateForm farmId={farmId} />
      <PrescriptionHistoryList farmId={farmId} />
    </main>
  );
}
