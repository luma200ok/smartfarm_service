import type { Metadata } from "next";
import FarmControlPanel from "@/components/control/FarmControlPanel";

export const metadata: Metadata = {
  title: "제어 | 스마트팜",
};

export default async function FarmControlPage(props: PageProps<"/farms/[farmId]/control">) {
  const { farmId } = await props.params;
  return <FarmControlPanel farmId={farmId} />;
}
