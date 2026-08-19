import type { Metadata } from "next";
import SignupForm from "@/components/auth/SignupForm";

export const metadata: Metadata = {
  title: "회원가입 | 스마트팜",
};

export default function SignupPage() {
  return <SignupForm />;
}
