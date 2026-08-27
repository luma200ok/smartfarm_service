"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import ThemeToggle from "@/components/ui/ThemeToggle";
import { logout } from "@/lib/api/auth";
import { useAppShellContext } from "./AppShellContext";
import { NAV_GROUPS, getLeafHref, type NavGroup } from "./nav-config";

// 더보기 화면(이슈 #147, 시안 m4-more) — 하단 탭에 없는 나머지 대분류(데이터·제어의 나머지·부가
// 서비스·관리)를 섹션 리스트로 모은다. 항목·라벨은 nav-config.ts의 NAV_GROUPS를 그대로 순회해서
// 만든다 — 여기서 새로 라벨을 하드코딩하면 데스크톱 SideNav와 표기가 갈라질 수 있다(재사용 원칙,
// resolveMobileTitle과 같은 이유).
//
// 시안의 "신규 1"·"이상 2"·"27° 흐림"·"앱 버전 2.4.1" 같은 배지·수치는 대응하는 저비용 API가
// 이 화면 레벨엔 없어 렌더하지 않는다(#128·#129·#136·#139·#142·#144와 같은 원칙 — 없는 데이터를
// 지어내지 않는다). 대신 데스크톱에선 ProfileMenu에만 있던 다크모드·초대코드·로그아웃을 여기에
// 옮겨온다 — GlobalBar(ProfileMenu 포함)가 모바일에서 완전히 숨겨지므로, 이게 없으면 모바일에서
// 로그아웃할 방법 자체가 없어진다.
const HOME_TAB_GROUP_KEY = "dashboard";

export default function MobileMore() {
  const router = useRouter();
  const { farms, effectiveFarmId } = useAppShellContext();
  const [submitting, setSubmitting] = useState(false);

  async function handleLogout() {
    setSubmitting(true);
    try {
      await logout();
    } catch {
      // 서버 로그아웃 실패 무시 — 로컬 토큰은 logout()의 finally에서 이미 클리어됨(ProfileMenu와 동일)
    } finally {
      router.replace("/login");
    }
  }

  // 홈(대시보드) 그룹은 하단 "홈" 탭이 이미 커버한다 — 나머지 그룹만 섹션으로 보여준다.
  const sections = NAV_GROUPS.filter((g) => g.key !== HOME_TAB_GROUP_KEY);

  return (
    <div className="flex flex-col gap-3 px-4 py-3">
      {sections.map((group) => (
        <MoreSection
          key={group.key}
          group={group}
          effectiveFarmId={effectiveFarmId}
        />
      ))}

      <div className="rounded-[10px] border border-dp-line bg-dp-surface">
        <Link
          href="/invitations"
          className="flex min-h-11 items-center border-b border-dp-line-row px-4 text-[13.5px] font-medium text-dp-ink"
        >
          초대코드 입력
        </Link>
        <div className="flex min-h-11 items-center justify-between px-4">
          <span className="text-[13.5px] font-medium text-dp-ink">
            다크 모드
          </span>
          <ThemeToggle />
        </div>
      </div>

      <button
        type="button"
        onClick={handleLogout}
        disabled={submitting}
        className="min-h-11 rounded-[10px] border border-dp-line bg-dp-surface text-[13.5px] font-medium text-dp-body disabled:opacity-60"
      >
        로그아웃
      </button>

      {farms !== null && farms.length === 0 && (
        <p className="px-1 text-[12px] text-dp-sub">
          등록된 농장이 없습니다.{" "}
          <Link href="/farms" className="underline">
            농장 만들기 / 합류하기
          </Link>
        </p>
      )}
    </div>
  );
}

function MoreSection({
  group,
  effectiveFarmId,
}: {
  group: NavGroup;
  effectiveFarmId: string | null;
}) {
  return (
    <div className="overflow-hidden rounded-[10px] border border-dp-line bg-dp-surface">
      <div className="border-b border-dp-line-row bg-dp-inset-alt px-4 py-[9px] font-mono text-[10.5px] leading-none font-semibold tracking-[0.06em] text-dp-muted">
        {group.label}
      </div>
      {group.items.map((item) => {
        const href = getLeafHref(item, effectiveFarmId);
        if (!href) {
          return (
            <span
              key={item.label}
              aria-disabled="true"
              className="flex min-h-11 items-center border-b border-dp-line-row px-4 text-[13.5px] font-medium text-dp-faint last:border-b-0"
            >
              {item.label}
            </span>
          );
        }
        return (
          <Link
            key={item.label}
            href={href}
            className="flex min-h-11 items-center border-b border-dp-line-row px-4 text-[13.5px] font-medium text-dp-ink last:border-b-0"
          >
            {item.label}
          </Link>
        );
      })}
    </div>
  );
}
