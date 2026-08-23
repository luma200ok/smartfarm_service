import type { Metadata } from "next";
import FarmDeviceManagement from "@/components/monitoring/FarmDeviceManagement";

export const metadata: Metadata = {
  title: "장비 · 센서 관리 | 스마트팜",
};

export default async function FarmDevicesPage(props: PageProps<"/farms/[farmId]/devices">) {
  const { farmId } = await props.params;
  return <FarmDeviceManagement farmId={farmId} />;
}
