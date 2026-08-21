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
      <PrescriptionCreateForm farmId={farmId} />
      <PrescriptionHistoryList farmId={farmId} />
    </main>
  );
}
