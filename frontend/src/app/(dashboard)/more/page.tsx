import type { Metadata } from "next";
import Link from "next/link";
import MobileMore from "@/components/layout/MobileMore";

export const metadata: Metadata = {
  title: "더보기 | 스마트팜",
};

// 더보기 화면(이슈 #147, 시안 m4-more) — 모바일 하단 탭 4번째. farm 스코프 밖 라우트라
// GlobalBar 농장 드롭다운의 "농장 목록 전체 보기"(/farms)와 같은 성격(이슈 #133 IA 매핑)이다.
//
// 데스크톱 내비에는 이 경로로 가는 링크가 없지만(북마크·공유 링크로만 도달), 리뷰 P3 —
// min-[768px]:hidden 가드만 걸면 데스크톱에서 빈 화면이 된다. 여기 담긴 항목은 전부 좌측
// SideNav(≥768px)에서도 동일하게 도달 가능하므로, 리다이렉트 대신 "SideNav를 쓰라"는
// 안내 + /dashboard 링크로 대체한다(모바일 렌더는 완전히 그대로, 회귀 없음).
export default function MorePage() {
  return (
    <>
      <div className="min-[768px]:hidden">
        <MobileMore />
      </div>
      <div className="hidden flex-col items-center justify-center gap-3 px-6 py-20 text-center min-[768px]:flex">
        <p className="text-sm text-dp-sub">
          이 화면은 모바일 전용 레이아웃입니다. 같은 메뉴는 좌측 내비게이션에서
          찾을 수 있습니다.
        </p>
        <Link
          href="/dashboard"
          className="rounded-md bg-dp-ink px-4 py-2 text-[13px] font-semibold text-dp-surface"
        >
          대시보드로 이동
        </Link>
      </div>
    </>
  );
}
