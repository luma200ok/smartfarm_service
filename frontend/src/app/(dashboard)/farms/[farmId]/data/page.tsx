import type { Metadata } from "next";
import FarmDataAnalysis from "@/components/monitoring/FarmDataAnalysis";

export const metadata: Metadata = {
  title: "데이터 분석 | 스마트팜",
};

export default async function FarmDataPage(props: PageProps<"/farms/[farmId]/data">) {
  const { farmId } = await props.params;
  return <FarmDataAnalysis farmId={farmId} />;
}
