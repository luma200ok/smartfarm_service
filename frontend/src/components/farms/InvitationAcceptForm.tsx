"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Card, CardTitle } from "@/components/monitoring/ui";
import FormField from "@/components/ui/FormField";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { acceptInvitation } from "@/lib/api/farms";
import { notifyFarmsChanged } from "@/lib/farmsBus";

interface InvitationAcceptFormProps {
  // "card": 단독 페이지(/invitations)에서 카드 테두리+제목 포함(기존 동작, 기본값).
  // "plain": 모달 내부처럼 바깥에서 이미 카드/제목을 그리는 컨텍스트에서 중복 제거.
  variant?: "card" | "plain";
}

// 초대코드 입력 → 수락 화면 (contract §3 POST /api/invitations/accept).
// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card·CardTitle)로 통일한다(이슈 #109).
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
      notifyFarmsChanged(); // Sidebar(#42) 농장 리스트 재조회
      router.push(`/farms/${farm.id}`);
      router.refresh();
    } catch (err) {
      setError(resolveErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  const form = (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      {variant === "card" && <CardTitle as="h2">초대코드로 농장 합류</CardTitle>}
      <FormField
        id="invitation-code"
        label="초대코드"
        type="text"
        required
        value={code}
        onChange={(e) => setCode(e.target.value)}
      />
      {error && <p className="text-sm text-dp-red-ink">{error}</p>}
      <button
        type="submit"
        disabled={submitting}
        className="self-start rounded-md bg-dp-ink px-4 py-2 text-sm font-medium text-dp-surface transition-colors hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
      >
        {submitting ? "확인 중..." : "합류하기"}
      </button>
    </form>
  );

  if (variant === "plain") return form;

  return <Card className="p-4">{form}</Card>;
}
