import type { Metadata } from "next";
import Link from "next/link";
import LogoutButton from "@/components/auth/LogoutButton";
import FarmSummaryList from "@/components/farms/FarmSummaryList";

export const metadata: Metadata = {
  title: "대시보드 | 스마트팜",
};

// 대시보드 홈 — 내 농장 목록 요약. 농장이 없으면 생성/합류로 유도한다. (이슈 #6)
export default function DashboardPage() {
  return (
    <div className="flex flex-1 flex-col">
      <header className="flex items-center justify-between border-b border-zinc-200 px-6 py-4 dark:border-zinc-800">
        <h1 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">스마트팜</h1>
        <LogoutButton />
      </header>
      <main className="flex flex-col gap-6 px-6 py-6">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">내 농장</h2>
          <div className="flex gap-3 text-sm">
            <Link href="/farms" className="text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50">
              전체보기
            </Link>
            <Link href="/invitations" className="text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50">
              초대코드 입력
            </Link>
          </div>
        </div>
        <FarmSummaryList
          emptyHint={
            <>
              아직 등록된 농장이 없습니다.{" "}
              <Link href="/farms" className="underline">
                농장을 만들거나 초대코드로 합류
              </Link>
              해보세요.
            </>
          }
        />
      </main>
    </div>
  );
}
