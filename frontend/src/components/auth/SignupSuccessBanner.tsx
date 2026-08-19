"use client";

import { useSearchParams } from "next/navigation";

export default function SignupSuccessBanner() {
  const searchParams = useSearchParams();
  if (searchParams.get("signup") !== "success") {
    return null;
  }

  return (
    <p className="rounded-md bg-green-50 px-3 py-2 text-sm text-green-700 dark:bg-green-950 dark:text-green-400">
      회원가입이 완료되었습니다. 로그인해주세요.
    </p>
  );
}
