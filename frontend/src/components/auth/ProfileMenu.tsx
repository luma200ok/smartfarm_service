"use client";

import { useRouter } from "next/navigation";
import { useEffect, useId, useRef, useState } from "react";
import { getMe, logout } from "@/lib/api/auth";
import type { UserResponse } from "@/types";

// 우측 상단 프로필 메뉴(이슈 #47) — 사이드바 하단/모바일 헤더에 흩어져 있던 로그아웃을
// 통합하고, 로그인한 사용자 정보(닉네임·이메일)를 노출한다.
// getMe()는 이 컴포넌트 마운트 시 1회만 조회한다(중복 조회 금지 관례 — #44 참고).
// 조회 실패는 화면에 에러를 띄우지 않고 아바타를 '?'로 조용히 대체한다.
// 항목이 로그아웃 1개뿐이라 role="menu"/"menuitem"(화살표키 내비 기대) 대신 단순
// disclosure 패턴(트리거 aria-expanded/aria-controls + 일반 div 패널)을 사용한다.
export default function ProfileMenu() {
  const router = useRouter();
  const [user, setUser] = useState<UserResponse | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [open, setOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const logoutButtonRef = useRef<HTMLButtonElement>(null);
  const panelId = useId();

  useEffect(() => {
    let cancelled = false;
    getMe()
      .then((data) => {
        if (!cancelled) setUser(data);
      })
      .catch(() => {
        // authFetch가 401은 이미 자체 처리(refresh/리다이렉트)한다 — 여기서는 그 외
        // 실패(네트워크 오류 등)만 조용히 흡수하고 기본 아바타로 대체한다.
        if (!cancelled) setLoadFailed(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // 외부 클릭 닫기 — 닫히면 트리거로 포커스를 복귀시켜 키보드 사용자가 위치를 잃지 않게 한다.
  useEffect(() => {
    if (!open) return;

    function handleClickOutside(e: MouseEvent) {
      if (!containerRef.current?.contains(e.target as Node)) {
        setOpen(false);
        triggerRef.current?.focus();
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  // ESC 닫기(트리거로 포커스 복귀) + 열릴 때 첫 항목(로그아웃)으로 경량 포커스 이동
  // (모달이 아니므로 풀 트랩 불요)
  useEffect(() => {
    if (!open) return;

    logoutButtonRef.current?.focus();

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") {
        setOpen(false);
        triggerRef.current?.focus();
      }
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [open]);

  async function handleLogout() {
    setSubmitting(true);
    try {
      await logout();
    } catch {
      // 서버 로그아웃 실패 무시 — 로컬 토큰은 logout()의 finally에서 이미 클리어됨
    } finally {
      router.replace("/login");
    }
  }

  const initial = user?.nickname ? user.nickname.charAt(0).toUpperCase() : loadFailed ? "?" : "";
  const triggerLabel = user ? `${user.nickname} 프로필 메뉴` : "프로필 메뉴";

  return (
    <div ref={containerRef} className="relative">
      <button
        ref={triggerRef}
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        aria-label={triggerLabel}
        onClick={() => setOpen((prev) => !prev)}
        className="flex items-center gap-2 rounded-md px-1.5 py-1 text-sm transition-colors hover:bg-zinc-100 dark:hover:bg-zinc-900"
      >
        <span
          aria-hidden="true"
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-zinc-200 text-sm font-semibold text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300"
        >
          {initial}
        </span>
        {user && (
          <span className="hidden max-w-[8rem] truncate font-medium text-zinc-700 sm:inline dark:text-zinc-300">
            {user.nickname}
          </span>
        )}
      </button>

      {open && (
        <div
          id={panelId}
          className="absolute right-0 z-20 mt-2 w-56 rounded-lg border border-zinc-200 bg-white p-1 shadow-lg dark:border-zinc-800 dark:bg-zinc-950"
        >
          {user && (
            <>
              <div className="px-3 py-2">
                <p className="truncate text-sm font-semibold text-zinc-900 dark:text-zinc-50">
                  {user.nickname}
                </p>
                <p className="truncate text-xs text-zinc-500 dark:text-zinc-400">{user.email}</p>
              </div>
              <div className="my-1 border-t border-zinc-200 dark:border-zinc-800" />
            </>
          )}
          <button
            ref={logoutButtonRef}
            type="button"
            onClick={handleLogout}
            disabled={submitting}
            className="w-full rounded-md px-3 py-2 text-left text-sm font-medium text-zinc-700 transition-colors hover:bg-zinc-100 disabled:cursor-not-allowed disabled:opacity-60 dark:text-zinc-300 dark:hover:bg-zinc-900"
          >
            로그아웃
          </button>
        </div>
      )}
    </div>
  );
}
