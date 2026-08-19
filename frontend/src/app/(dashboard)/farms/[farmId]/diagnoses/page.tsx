import type { Metadata } from "next";
import DashboardHeader from "@/components/layout/DashboardHeader";
import DiagnosesPageClient from "@/components/diagnoses/DiagnosesPageClient";

export const metadata: Metadata = {
  title: "진단 | 스마트팜",
};

export default async function FarmDiagnosesPage(props: PageProps<"/farms/[farmId]/diagnoses">) {
  const { farmId } = await props.params;
  return (
    <div className="flex flex-1 flex-col">
      <DashboardHeader title="진단" backHref={`/farms/${farmId}`} />
      <DiagnosesPageClient farmId={farmId} />
    </div>
  );
}
