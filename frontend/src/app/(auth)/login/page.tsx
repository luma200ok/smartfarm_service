import type { Metadata } from "next";
import { Suspense } from "react";
import LoginForm from "@/components/auth/LoginForm";
import LoginRedirectGuard from "@/components/auth/LoginRedirectGuard";
import SignupSuccessBanner from "@/components/auth/SignupSuccessBanner";

export const metadata: Metadata = {
  title: "로그인 | 스마트팜",
};

export default function LoginPage() {
  return (
    <div className="flex flex-col gap-4">
      <LoginRedirectGuard />
      <Suspense fallback={null}>
        <SignupSuccessBanner />
      </Suspense>
      <LoginForm />
    </div>
  );
}
