import type { Metadata } from "next";
import MobileMore from "@/components/layout/MobileMore";

export const metadata: Metadata = {
  title: "더보기 | 스마트팜",
};

// 더보기 화면(이슈 #147, 시안 m4-more) — 모바일 하단 탭 4번째. farm 스코프 밖 라우트라
// GlobalBar 농장 드롭다운의 "농장 목록 전체 보기"(/farms)와 같은 성격(이슈 #133 IA 매핑)이다.
export default function MorePage() {
  return <MobileMore />;
}
