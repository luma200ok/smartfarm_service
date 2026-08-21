"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import FormField from "@/components/ui/FormField";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { acceptInvitation } from "@/lib/api/farms";

interface InvitationAcceptFormProps {
  // "card": 단독 페이지(/invitations)에서 카드 테두리+제목 포함(기존 동작, 기본값).
  // "plain": 모달 내부처럼 바깥에서 이미 카드/제목을 그리는 컨텍스트에서 중복 제거.
  variant?: "card" | "plain";
}

// 초대코드 입력 → 수락 화면 (contract §3 POST /api/invitations/accept).
export default function InvitationAcceptForm({ variant = "card" }: InvitationAcceptFormProps) {
  const router = useRouter();
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    if (!code.trim()) {
      setError("초대코드를 입력해주세요.");
      return;
    }

    setSubmitting(true);
    try {
      const farm = await acceptInvitation({ code: code.trim() });
      router.push(`/farms/${farm.id}`);
      router.refresh();
    } catch (err) {
      setError(resolveErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className={
        variant === "card"
          ? "flex flex-col gap-4 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800"
          : "flex flex-col gap-4"
      }
    >
      {variant === "card" && (
        <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">초대코드로 농장 합류</h2>
      )}
      <FormField
        id="invitation-code"
        label="초대코드"
        type="text"
        required
        value={code}
        onChange={(e) => setCode(e.target.value)}
      />
      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
      <button
        type="submit"
        disabled={submitting}
        className="self-start rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
      >
        {submitting ? "확인 중..." : "합류하기"}
      </button>
    </form>
  );
}
