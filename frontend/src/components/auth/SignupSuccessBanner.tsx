"use client";

import { useSearchParams } from "next/navigation";

export default function SignupSuccessBanner() {
  const searchParams = useSearchParams();
  if (searchParams.get("signup") !== "success") {
    return null;
  }

  return (
    <p className="rounded-md bg-dp-green-tint px-3 py-2 text-sm text-dp-green-ink">
      회원가입이 완료되었습니다. 로그인해주세요.
    </p>
  );
}
