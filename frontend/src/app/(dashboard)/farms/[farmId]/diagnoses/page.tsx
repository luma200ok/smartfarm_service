import type { Metadata } from "next";
import DiagnosesPageClient from "@/components/diagnoses/DiagnosesPageClient";

export const metadata: Metadata = {
  title: "진단 | 스마트팜",
};

export default async function FarmDiagnosesPage(props: PageProps<"/farms/[farmId]/diagnoses">) {
  const { farmId } = await props.params;
  return <DiagnosesPageClient farmId={farmId} />;
}
