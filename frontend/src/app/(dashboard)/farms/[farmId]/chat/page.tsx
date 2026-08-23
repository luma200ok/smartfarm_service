import type { Metadata } from "next";
import FarmChat from "@/components/chat/FarmChat";

export const metadata: Metadata = {
  title: "AI 상담 | 스마트팜",
};

export default async function FarmChatPage(props: PageProps<"/farms/[farmId]/chat">) {
  const { farmId } = await props.params;
  return <FarmChat farmId={farmId} />;
}
