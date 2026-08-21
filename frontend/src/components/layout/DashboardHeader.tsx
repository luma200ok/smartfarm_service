"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import ProfileMenu from "@/components/auth/ProfileMenu";
import ThemeToggle from "@/components/ui/ThemeToggle";

interface DashboardHeaderProps {
  title?: string;
  backHref?: string;
}

interface NavItem {
  href: string;
  label: string;
  isActive: (pathname: string) => boolean;
}

// 전 화면 공통 내비게이션(이슈 #38) — /farms 하위 상세 경로도 '농장 목록'을 활성 처리한다.
// Sidebar(이슈 #42)도 같은 항목을 재사용하므로 export한다.
export const NAV_ITEMS: NavItem[] = [
  { href: "/dashboard", label: "대시보드", isActive: (pathname) => pathname === "/dashboard" },
  {
    href: "/farms",
    label: "농장 목록",
    isActive: (pathname) => pathname === "/farms" || pathname.startsWith("/farms/"),
  },
  { href: "/invitations", label: "초대코드", isActive: (pathname) => pathname === "/invitations" },
];

// 전역 공통 헤더 — 로고(홈 링크)+내비(현재 위치 활성 표시)+테마 토글+프로필 메뉴(이슈 #47).
// 하위 상세 화면은 title/backHref로 부가 컨텍스트(뒤로가기)를 표시할 수 있다.
// 데스크톱(lg 이상)은 Sidebar(이슈 #42)가 내비·테마 토글을 상시 노출하므로 헤더에서는
// 해당 항목을 숨기고 title/backHref + 우측 프로필 메뉴만 남긴다(title 없는 페이지에서도
// 프로필 메뉴는 항상 우측에 노출 — 이슈 #47).
export default function DashboardHeader({ title, backHref }: DashboardHeaderProps) {
  const pathname = usePathname() ?? "";

  return (
    <header className="flex flex-col gap-2 border-b border-zinc-200 px-6 py-4 dark:border-zinc-800">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        {/* lg 이상은 Sidebar(이슈 #42)가 동일 로고를 상시 노출하므로 중복 방지 */}
        <Link
          href="/dashboard"
          className="text-lg font-semibold text-zinc-900 lg:hidden dark:text-zinc-50"
        >
          스마트팜
        </Link>
        <nav className="flex flex-wrap items-center gap-4 text-sm lg:hidden">
          {NAV_ITEMS.map((item) => {
            const active = item.isActive(pathname);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={
                  active
                    ? "font-semibold text-zinc-900 dark:text-zinc-50"
                    : "text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50"
                }
              >
                {item.label}
              </Link>
            );
          })}
          <ThemeToggle />
          <ProfileMenu />
        </nav>
        {/* lg 이상: title 유무와 무관하게 우측 정렬로 프로필 메뉴 상시 노출 */}
        <div className="ml-auto hidden lg:block">
          <ProfileMenu />
        </div>
      </div>
      {(title || backHref) && (
        <div className="flex items-center gap-3 text-sm">
          {backHref && (
            <Link
              href={backHref}
              className="text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50"
            >
              ← 뒤로
            </Link>
          )}
          {title && <span className="font-medium text-zinc-700 dark:text-zinc-300">{title}</span>}
        </div>
      )}
    </header>
  );
}
