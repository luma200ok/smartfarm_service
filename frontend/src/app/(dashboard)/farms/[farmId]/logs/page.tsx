import type { Metadata } from "next";
import FarmLogList from "@/components/logs/FarmLogList";

export const metadata: Metadata = {
  title: "작업일지 | 스마트팜",
};

export default async function FarmLogsPage(props: PageProps<"/farms/[farmId]/logs">) {
  const { farmId } = await props.params;
  return <FarmLogList farmId={farmId} />;
}
