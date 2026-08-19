"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import FormField from "@/components/ui/FormField";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { acceptInvitation } from "@/lib/api/farms";

// 초대코드 입력 → 수락 화면 (contract §3 POST /api/invitations/accept).
export default function InvitationAcceptForm() {
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
    <form onSubmit={handleSubmit} className="flex flex-col gap-4 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
      <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">초대코드로 농장 합류</h2>
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
