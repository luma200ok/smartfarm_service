"use client";

import { useSyncExternalStore } from "react";
import { STORAGE_KEYS } from "@/constants";

// 클릭으로 상태를 바꿀 때 구독자(useSyncExternalStore)에게 알리기 위한 최소 pub-sub.
// 인스턴스가 여러 곳(헤더 등)에 동시에 떠 있어도 하나가 토글하면 전부 동기화된다.
const listeners = new Set<() => void>();

function subscribe(callback: () => void) {
  listeners.add(callback);
  return () => listeners.delete(callback);
}

function getSnapshot() {
  return document.documentElement.classList.contains("dark");
}

// 서버 렌더링 시점엔 document가 없어 항상 false로 고정 — layout.tsx의 no-flash 스크립트가
// 첫 페인트 전에 실제 .dark 클래스를 적용해두므로, useSyncExternalStore가 하이드레이션 커밋 시
// getSnapshot()과의 차이를 깜빡임 없이 동기 보정한다(react-hooks/set-state-in-effect 회피).
function getServerSnapshot() {
  return false;
}

function setDark(next: boolean) {
  document.documentElement.classList.toggle("dark", next);
  try {
    localStorage.setItem(STORAGE_KEYS.theme, next ? "dark" : "light");
  } catch {
    // localStorage 접근 실패(프라이빗 모드 등) — 저장은 실패해도 화면 전환은 유지
  }
  listeners.forEach((listener) => listener());
}

// 라이트/다크 모드 토글(이슈 #36) — 필(pill) 스위치 UI(이슈 #38).
// 트랙: 라이트=zinc-200, 다크=zinc-800. 노브는 현재 모드 아이콘을 담고 좌우로 슬라이딩한다.
export default function ThemeToggle() {
  const isDark = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  return (
    <button
      type="button"
      role="switch"
      aria-checked={isDark}
      aria-label="다크 모드"
      onClick={() => setDark(!isDark)}
      className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full border transition-colors duration-200 ease-in-out focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-400 ${
        isDark ? "border-zinc-700 bg-zinc-800" : "border-zinc-300 bg-zinc-200"
      }`}
    >
      <span
        aria-hidden="true"
        className={`inline-flex h-5 w-5 items-center justify-center rounded-full bg-white text-[10px] leading-none shadow transition-transform duration-200 ease-in-out dark:bg-zinc-950 ${
          isDark ? "translate-x-[22px]" : "translate-x-0.5"
        }`}
      >
        {isDark ? "🌙" : "☀️"}
      </span>
    </button>
  );
}
