"use client";

// 프리뷰 전용 플로팅 툴바(이슈 #83) — 아트보드 사이 이동 + 테마 토글.
// 시안에는 없는 요소라 우하단에 작게 띄우고 평소엔 흐리게(opacity-35) 둔다 — 시안 화면의
// 우하단 버튼과 겹치기 때문. 마우스를 올리거나 포커스가 들어오면 또렷해진다.
// 아트보드 자체 색이 아닌 중립 색(zinc)을 써서 시안 색과 섞이지 않게 했다.

import Link from "next/link";
import { usePathname } from "next/navigation";
import ThemeToggle from "@/components/ui/ThemeToggle";
import { ARTBOARDS } from "./mock";

const FLAT = ARTBOARDS.flatMap((group) => group.boards);

export default function PreviewToolbar() {
  const pathname = usePathname() ?? "";
  const index = FLAT.findIndex((board) => board.href === pathname);
  const current = index >= 0 ? FLAT[index] : null;
  const prev = index > 0 ? FLAT[index - 1] : null;
  const next = index >= 0 && index < FLAT.length - 1 ? FLAT[index + 1] : null;

  return (
    <div className="fixed right-4 bottom-4 z-50 flex items-center gap-2 rounded-full border border-zinc-300 bg-white/90 px-2 py-1.5 opacity-35 shadow-lg backdrop-blur transition-opacity hover:opacity-100 focus-within:opacity-100 dark:border-zinc-700 dark:bg-zinc-900/90">
      <Link
        href="/design-preview"
        className="rounded-full px-2.5 py-1 font-mono text-[11px] font-semibold text-zinc-600 hover:bg-zinc-100 dark:text-zinc-300 dark:hover:bg-zinc-800"
      >
        시안 목록
      </Link>
      {current ? (
        <>
          <span className="h-3.5 w-px bg-zinc-300 dark:bg-zinc-700" />
          <NavArrow to={prev?.href} label="이전 아트보드">
            ←
          </NavArrow>
          <span className="font-mono text-[11px] font-semibold text-zinc-900 dark:text-zinc-100">{current.id}</span>
          <NavArrow to={next?.href} label="다음 아트보드">
            →
          </NavArrow>
        </>
      ) : null}
      <span className="h-3.5 w-px bg-zinc-300 dark:bg-zinc-700" />
      <ThemeToggle />
    </div>
  );
}

function NavArrow({ to, label, children }: { to?: string; label: string; children: string }) {
  if (!to) {
    return (
      <span aria-hidden className="px-1.5 text-[13px] leading-none text-zinc-300 dark:text-zinc-700">
        {children}
      </span>
    );
  }
  return (
    <Link
      href={to}
      aria-label={label}
      className="rounded-full px-1.5 py-0.5 text-[13px] leading-none text-zinc-600 hover:bg-zinc-100 dark:text-zinc-300 dark:hover:bg-zinc-800"
    >
      {children}
    </Link>
  );
}
