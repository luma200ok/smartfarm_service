import type { Metadata } from "next";
import DashboardHeader from "@/components/layout/DashboardHeader";
import DiagnosisDetail from "@/components/diagnoses/DiagnosisDetail";

export const metadata: Metadata = {
  title: "진단 상세 | 스마트팜",
};

export default async function DiagnosisDetailPage(
  props: PageProps<"/farms/[farmId]/diagnoses/[diagnosisId]">
) {
  const { farmId, diagnosisId } = await props.params;
  return (
    <div className="flex flex-1 flex-col">
      <DashboardHeader title="진단 상세" backHref={`/farms/${farmId}/diagnoses`} />
      <DiagnosisDetail farmId={farmId} diagnosisId={diagnosisId} />
    </div>
  );
}
