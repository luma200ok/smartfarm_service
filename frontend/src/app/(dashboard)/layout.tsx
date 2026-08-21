"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { isAuthenticated } from "@/lib/api/auth";

/**
 * (dashboard) 그룹 가드 — 뼈대만.
 * 토큰은 localStorage에만 있으므로(서버 컴포넌트/미들웨어에서 접근 불가) 클라이언트에서 검사한다.
 * 인증 없으면 /login 으로 리다이렉트, 확인 전까지는 아무것도 렌더링하지 않는다.
 *
 * 마운트 후 isAuthenticated()를 직접 호출해 판정한다(이슈 #33).
 * 이전엔 useSyncExternalStore(getServerSnapshot이 항상 false 고정)를 썼는데,
 * hydration 첫 렌더에서 authed=false로 잡히고 passive effect가 스토어 스냅샷
 * 갱신보다 먼저 실행돼 유효 토큰 보유 상태에서도 /login으로 튕기는 레이스가 있었다.
 */
export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [status, setStatus] = useState<"checking" | "authed">("checking");

  useEffect(() => {
    function check() {
      if (isAuthenticated()) {
        setStatus("authed");
      } else {
        router.replace("/login");
      }
    }

    check();

    // 같은 탭에서의 localStorage 변경은 storage 이벤트가 발생하지 않으므로 다른 탭 동기화용.
    // 다른 탭에서 로그아웃되면 이 탭도 재검사해 /login으로 보낸다.
    window.addEventListener("storage", check);
    return () => window.removeEventListener("storage", check);
  }, [router]);

  if (status !== "authed") {
    return null;
  }

  return <div className="flex flex-1 flex-col">{children}</div>;
}
