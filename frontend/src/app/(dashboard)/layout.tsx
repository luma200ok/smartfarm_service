"use client";

import { useRouter } from "next/navigation";
import { useEffect, useSyncExternalStore } from "react";
import { isAuthenticated } from "@/lib/api/auth";

// 같은 탭에서의 localStorage 변경은 storage 이벤트가 발생하지 않으므로 다른 탭 동기화용.
// 이 가드의 스냅샷은 마운트 시점 값이면 충분(로그인/로그아웃은 항상 페이지 전환을 동반).
function subscribe(callback: () => void) {
  if (typeof window === "undefined") return () => {};
  window.addEventListener("storage", callback);
  return () => window.removeEventListener("storage", callback);
}

function getServerSnapshot() {
  return false;
}

/**
 * (dashboard) 그룹 가드 — 뼈대만.
 * 토큰은 localStorage에만 있으므로(서버 컴포넌트/미들웨어에서 접근 불가) 클라이언트에서 검사한다.
 * 인증 없으면 /login 으로 리다이렉트, 확인 전까지는 아무것도 렌더링하지 않는다.
 */
export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const authed = useSyncExternalStore(subscribe, isAuthenticated, getServerSnapshot);

  useEffect(() => {
    if (!authed) {
      router.replace("/login");
    }
  }, [authed, router]);

  if (!authed) {
    return null;
  }

  return <div className="flex flex-1 flex-col">{children}</div>;
}
