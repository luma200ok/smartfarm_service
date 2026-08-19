import type { Metadata } from "next";
import DashboardHeader from "@/components/layout/DashboardHeader";
import PrescriptionPoller from "@/components/prescriptions/PrescriptionPoller";

export const metadata: Metadata = {
  title: "처방 상세 | 스마트팜",
};

export default async function PrescriptionDetailPage(
  props: PageProps<"/farms/[farmId]/prescriptions/[prescriptionId]">
) {
  const { farmId, prescriptionId } = await props.params;
  return (
    <div className="flex flex-1 flex-col">
      <DashboardHeader title="처방 상세" backHref={`/farms/${farmId}/prescriptions`} />
      <PrescriptionPoller farmId={farmId} prescriptionId={prescriptionId} />
    </div>
  );
}
