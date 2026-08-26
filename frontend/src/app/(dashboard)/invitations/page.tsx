import type { Metadata } from "next";
import InvitationAcceptForm from "@/components/farms/InvitationAcceptForm";

export const metadata: Metadata = {
  title: "초대코드 입력 | 스마트팜",
};

// farm 스코프 밖 화면(이슈 #133 IA) — 좌측 내비 대상이 아니라 프로필 메뉴의
// "초대코드 입력"으로 도달한다.
export default function InvitationsPage() {
  return (
    <div className="flex flex-1 flex-col">
      <h1 className="px-6 pt-6 text-lg font-semibold text-dp-ink">초대코드 입력</h1>
      <main className="px-6 py-6">
        <InvitationAcceptForm />
      </main>
    </div>
  );
}
