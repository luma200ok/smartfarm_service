"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { isAuthenticated } from "@/lib/api/auth";

/**
 * 로그인 페이지 역가드. 유효한 토큰을 이미 보유한 상태로 /login에 직접 진입하면
 * /dashboard로 되돌려보낸다(이슈 #33).
 */
export default function LoginRedirectGuard() {
  const router = useRouter();

  useEffect(() => {
    if (isAuthenticated()) {
      router.replace("/dashboard");
    }
  }, [router]);

  return null;
}
