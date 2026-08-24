"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import FormField from "@/components/ui/FormField";
import { DEFAULT_ERROR_MESSAGE, ERROR_MESSAGES, VALIDATION } from "@/constants";
import { demoLogin, login } from "@/lib/api/auth";
import { ApiError } from "@/lib/api/client";

export default function LoginForm() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [emailError, setEmailError] = useState<string | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [demoSubmitting, setDemoSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);

    if (!VALIDATION.email.pattern.test(email)) {
      setEmailError("이메일 형식이 올바르지 않습니다.");
      return;
    }
    setEmailError(undefined);

    setSubmitting(true);
    try {
      await login({ email, password });
      router.push("/dashboard");
    } catch (err) {
      if (err instanceof ApiError) {
        setError((err.code && ERROR_MESSAGES[err.code]) || err.message || DEFAULT_ERROR_MESSAGE);
      } else {
        setError(DEFAULT_ERROR_MESSAGE);
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDemoLogin() {
    setError(null);
    setEmailError(undefined);
    setDemoSubmitting(true);
    try {
      await demoLogin();
      router.push("/dashboard");
    } catch (err) {
      if (err instanceof ApiError) {
        setError((err.code && ERROR_MESSAGES[err.code]) || err.message || DEFAULT_ERROR_MESSAGE);
      } else {
        setError(DEFAULT_ERROR_MESSAGE);
      }
    } finally {
      setDemoSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5">
      <div>
        <h1 className="text-xl font-semibold text-dp-ink">로그인</h1>
        <p className="mt-1 text-sm text-dp-muted">스마트팜 서비스에 로그인하세요.</p>
      </div>

      <FormField
        id="email"
        label="이메일"
        type="email"
        autoComplete="email"
        required
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={emailError}
      />
      <FormField
        id="password"
        label="비밀번호"
        type="password"
        autoComplete="current-password"
        required
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />

      {error && <p className="text-sm text-dp-red-ink">{error}</p>}

      <button
        type="submit"
        disabled={submitting || demoSubmitting}
        className="rounded-md bg-dp-ink px-4 py-2 text-sm font-medium text-dp-surface disabled:opacity-40"
      >
        {submitting ? "로그인 중..." : "로그인"}
      </button>

      <button
        type="button"
        onClick={handleDemoLogin}
        disabled={submitting || demoSubmitting}
        className="rounded-md border border-dp-line-strong px-4 py-2 text-sm font-medium text-dp-sub hover:bg-dp-inset disabled:opacity-40"
      >
        {demoSubmitting ? "데모 로그인 중..." : "데모 계정으로 체험하기"}
      </button>

      <p className="text-center text-sm text-dp-muted">
        계정이 없으신가요?{" "}
        <Link href="/signup" className="font-medium text-dp-ink underline">
          회원가입
        </Link>
      </p>
    </form>
  );
}
