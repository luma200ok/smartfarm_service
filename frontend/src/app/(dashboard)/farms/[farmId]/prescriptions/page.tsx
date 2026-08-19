import type { Metadata } from "next";
import DashboardHeader from "@/components/layout/DashboardHeader";
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
    <div className="flex flex-1 flex-col">
      <DashboardHeader title="처방" backHref={`/farms/${farmId}`} />
      <main className="flex flex-col gap-8 px-6 py-6">
        <PrescriptionCreateForm farmId={farmId} />
        <PrescriptionHistoryList farmId={farmId} />
      </main>
    </div>
  );
}
