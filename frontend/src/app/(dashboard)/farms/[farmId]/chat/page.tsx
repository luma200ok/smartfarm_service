import type { Metadata } from "next";
import FarmServicesLayout from "@/components/chat/FarmServicesLayout";

export const metadata: Metadata = {
  title: "AI 상담 | 스마트팜",
};

export default async function FarmChatPage(props: PageProps<"/farms/[farmId]/chat">) {
  const { farmId } = await props.params;
  // #144 시안 05 — AI 챗봇 + 날씨 예보 + 농약 정보를 한 화면에 합성한다.
  return <FarmServicesLayout farmId={farmId} />;
}
