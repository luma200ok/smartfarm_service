"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
}

const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

// 공용 모달 — 농장 생성/합류처럼 짧은 폼을 목록 페이지 위에 띄울 때 사용.
// 라이브러리 의존 없이 React 기본 + Tailwind만으로 구현
// (ESC/backdrop 닫기, 포커스 트랩·복귀, body 스크롤 잠금, aria 연결).
export default function Modal({ open, onClose, title, children }: ModalProps) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  // 재렌더마다 새로 만들어지는 onClose(예: 인라인 화살표 함수)가 아래 effect들을
  // 재실행시켜 포커스를 다시 뺏지 않도록 최신 onClose를 ref로만 참조한다.
  // ref 갱신은 렌더 중이 아닌 effect에서 수행(react-hooks/refs).
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  });

  // 열릴 때: 이전 포커스 저장 + 다이얼로그로 포커스 이동. 닫힐 때: 이전 포커스로 복귀(WCAG 2.4.3).
  // deps를 [open]만 두어 onClose identity 변경으로는 재실행되지 않는다.
  useEffect(() => {
    if (!open) return;

    const previouslyFocused = document.activeElement as HTMLElement | null;
    dialogRef.current?.focus();

    return () => {
      previouslyFocused?.focus();
    };
  }, [open]);

  // ESC 닫기 + Tab 포커스 트랩. deps를 [open]만 두어 onClose identity 변경에 흔들리지 않는다.
  useEffect(() => {
    if (!open) return;

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") {
        onCloseRef.current();
        return;
      }
      if (e.key !== "Tab") return;

      const container = dialogRef.current;
      if (!container) return;
      const focusable = Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
      if (focusable.length === 0) {
        e.preventDefault();
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const active = document.activeElement;

      if (e.shiftKey) {
        if (active === first || !container.contains(active)) {
          e.preventDefault();
          last.focus();
        }
      } else {
        if (active === last || !container.contains(active)) {
          e.preventDefault();
          first.focus();
        }
      }
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [open]);

  // 열려 있는 동안 배경 스크롤 잠금.
  useEffect(() => {
    if (!open) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [open]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4"
      onClick={onClose}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
        className="flex w-full max-w-md flex-col gap-4 rounded-lg border border-zinc-200 bg-white p-4 shadow-lg outline-none dark:border-zinc-800 dark:bg-zinc-950"
      >
        <div className="flex items-center justify-between">
          <h2 id={titleId} className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-50"
          >
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
