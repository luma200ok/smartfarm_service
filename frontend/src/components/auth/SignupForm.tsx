"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import FormField from "@/components/ui/FormField";
import { DEFAULT_ERROR_MESSAGE, ERROR_MESSAGES, VALIDATION } from "@/constants";
import { signup } from "@/lib/api/auth";
import { ApiError } from "@/lib/api/client";

interface FieldErrors {
  email?: string;
  password?: string;
  nickname?: string;
}

function validate(email: string, password: string, nickname: string): FieldErrors {
  const errors: FieldErrors = {};
  if (!email) {
    errors.email = "이메일을 입력해주세요.";
  } else if (!VALIDATION.email.pattern.test(email)) {
    errors.email = "이메일 형식이 올바르지 않습니다.";
  }
  if (password.length < VALIDATION.password.minLength) {
    errors.password = `비밀번호는 ${VALIDATION.password.minLength}자 이상이어야 합니다.`;
  }
  if (
    nickname.length < VALIDATION.nickname.minLength ||
    nickname.length > VALIDATION.nickname.maxLength
  ) {
    errors.nickname = `닉네임은 ${VALIDATION.nickname.minLength}~${VALIDATION.nickname.maxLength}자여야 합니다.`;
  }
  return errors;
}

export default function SignupForm() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [nickname, setNickname] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);

    const errors = validate(email, password, nickname);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      await signup({ email, password, nickname });
      router.push("/login?signup=success");
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

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      <div>
        <h1 className="text-xl font-semibold text-zinc-900 dark:text-zinc-50">회원가입</h1>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          스마트팜 서비스 계정을 만드세요.
        </p>
      </div>

      <FormField
        id="email"
        label="이메일"
        type="email"
        autoComplete="email"
        required
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={fieldErrors.email}
      />
      <FormField
        id="password"
        label="비밀번호"
        type="password"
        autoComplete="new-password"
        required
        minLength={VALIDATION.password.minLength}
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={fieldErrors.password}
      />
      <FormField
        id="nickname"
        label="닉네임"
        type="text"
        autoComplete="nickname"
        required
        minLength={VALIDATION.nickname.minLength}
        maxLength={VALIDATION.nickname.maxLength}
        value={nickname}
        onChange={(e) => setNickname(e.target.value)}
        error={fieldErrors.nickname}
      />

      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

      <button
        type="submit"
        disabled={submitting}
        className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
      >
        {submitting ? "가입 중..." : "회원가입"}
      </button>

      <p className="text-center text-sm text-zinc-500 dark:text-zinc-400">
        이미 계정이 있으신가요?{" "}
        <Link href="/login" className="font-medium text-zinc-900 underline dark:text-zinc-50">
          로그인
        </Link>
      </p>
    </form>
  );
}
