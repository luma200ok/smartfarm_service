import Link from "next/link";
import LogoutButton from "@/components/auth/LogoutButton";

interface DashboardHeaderProps {
  title: string;
  backHref?: string;
}

// 대시보드 하위 화면 공용 헤더 — 제목·뒤로가기·전역 네비게이션·로그아웃.
export default function DashboardHeader({ title, backHref }: DashboardHeaderProps) {
  return (
    <header className="flex flex-col gap-3 border-b border-zinc-200 px-6 py-4 dark:border-zinc-800 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-center gap-3">
        {backHref && (
          <Link
            href={backHref}
            className="text-sm text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50"
          >
            ← 뒤로
          </Link>
        )}
        <h1 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">{title}</h1>
      </div>
      <nav className="flex items-center gap-4 text-sm">
        <Link href="/dashboard" className="text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50">
          홈
        </Link>
        <Link href="/farms" className="text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50">
          농장 목록
        </Link>
        <Link href="/invitations" className="text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50">
          초대코드 입력
        </Link>
        <LogoutButton />
      </nav>
    </header>
  );
}
