import type { Metadata } from "next";
import FarmOverview from "@/components/farms/FarmOverview";

export const metadata: Metadata = {
  title: "농장 상세 | 스마트팜",
};

export default async function FarmDetailPage(props: PageProps<"/farms/[farmId]">) {
  const { farmId } = await props.params;
  return <FarmOverview farmId={farmId} />;
}
