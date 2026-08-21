"use client";

import { useState } from "react";
import Modal from "@/components/ui/Modal";
import FarmCreateForm from "./FarmCreateForm";
import FarmSummaryList from "./FarmSummaryList";
import InvitationAcceptForm from "./InvitationAcceptForm";

type OpenModal = "create" | "join" | null;

// /farms 페이지 클라이언트 오케스트레이터 — 목록 중심 화면 + 생성/합류는 모달로 분리(이슈 #32).
export default function FarmsPageClient() {
  const [openModal, setOpenModal] = useState<OpenModal>(null);

  return (
    <main className="flex flex-col gap-6 px-6 py-6">
      <div className="flex justify-end gap-2">
        <button
          type="button"
          onClick={() => setOpenModal("join")}
          className="rounded-md border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-900"
        >
          초대코드로 합류
        </button>
        <button
          type="button"
          onClick={() => setOpenModal("create")}
          className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-700 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
        >
          + 새 농장
        </button>
      </div>

      <FarmSummaryList emptyHint="우측 상단 버튼으로 농장을 만들거나 합류하세요." />

      <Modal open={openModal === "create"} onClose={() => setOpenModal(null)} title="새 농장 만들기">
        <FarmCreateForm variant="plain" />
      </Modal>

      <Modal open={openModal === "join"} onClose={() => setOpenModal(null)} title="초대코드로 농장 합류">
        <InvitationAcceptForm variant="plain" />
      </Modal>
    </main>
  );
}
