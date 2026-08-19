import type { Metadata } from "next";
import LogoutButton from "@/components/auth/LogoutButton";

export const metadata: Metadata = {
  title: "대시보드 | 스마트팜",
};

// 빈 대시보드 placeholder — 농장 목록/생성은 이슈 #6에서 구현.
export default function DashboardPage() {
  return (
    <div className="flex flex-1 flex-col">
      <header className="flex items-center justify-between border-b border-zinc-200 px-6 py-4 dark:border-zinc-800">
        <h1 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">스마트팜</h1>
        <LogoutButton />
      </header>
      <main className="flex flex-1 items-center justify-center px-6 py-16">
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          아직 등록된 농장이 없습니다. 농장 생성/참여 기능은 곧 추가됩니다.
        </p>
      </main>
    </div>
  );
}
