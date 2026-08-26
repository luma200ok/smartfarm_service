import type { Metadata } from "next";
import AlarmsPageClient from "@/components/alarms/AlarmsPageClient";

export const metadata: Metadata = {
  title: "알람 현황 | 스마트팜",
};

export default async function FarmAlarmsPage(props: PageProps<"/farms/[farmId]/alarms">) {
  const { farmId } = await props.params;
  return <AlarmsPageClient farmId={farmId} />;
}
