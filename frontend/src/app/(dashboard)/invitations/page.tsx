import type { Metadata } from "next";
import DashboardHeader from "@/components/layout/DashboardHeader";
import InvitationAcceptForm from "@/components/farms/InvitationAcceptForm";

export const metadata: Metadata = {
  title: "초대코드 입력 | 스마트팜",
};

export default function InvitationsPage() {
  return (
    <div className="flex flex-1 flex-col">
      <DashboardHeader title="초대코드 입력" backHref="/dashboard" />
      <main className="px-6 py-6">
        <InvitationAcceptForm />
      </main>
    </div>
  );
}
