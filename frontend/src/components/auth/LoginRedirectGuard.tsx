"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { isAuthenticated } from "@/lib/api/auth";

/**
 * 로그인 페이지 역가드. 유효한 토큰을 이미 보유한 상태로 /login에 직접 진입하면
 * /dashboard로 되돌려보낸다(이슈 #33).
 * 대시보드 가드와 대칭으로 children을 감싸서, 판정 전(checking)에는 로그인 폼을
 * 렌더하지 않는다 — 인증된 사용자가 /login에 진입했을 때 리다이렉트 전에 폼이
 * 짧게 노출되는 플래시를 막기 위함.
 */
export default function LoginRedirectGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [status, setStatus] = useState<"checking" | "unauthenticated">("checking");

  useEffect(() => {
    function check() {
      if (isAuthenticated()) {
        router.replace("/dashboard");
      } else {
        setStatus("unauthenticated");
      }
    }

    check();
  }, [router]);

  if (status !== "unauthenticated") {
    return null;
  }

  return <>{children}</>;
}
