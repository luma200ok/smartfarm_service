import type { Metadata } from "next";
import DashboardHeader from "@/components/layout/DashboardHeader";
import FarmDetail from "@/components/farms/FarmDetail";

export const metadata: Metadata = {
  title: "농장 상세 | 스마트팜",
};

export default async function FarmDetailPage(props: PageProps<"/farms/[farmId]">) {
  const { farmId } = await props.params;
  return (
    <div className="flex flex-1 flex-col">
      <DashboardHeader title="농장 상세" backHref="/farms" />
      <FarmDetail farmId={farmId} />
    </div>
  );
}
