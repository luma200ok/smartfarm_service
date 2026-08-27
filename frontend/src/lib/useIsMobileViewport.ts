"use client";

import { useSyncExternalStore } from "react";

// 모바일 전용 레이아웃 분기(이슈 #147)용 뷰포트 훅 — useIsDarkMode(lib/useIsDarkMode.ts)와 동일한
// useSyncExternalStore 패턴(외부 시스템=matchMedia 구독, react-hooks/set-state-in-effect 회피).
// 레이아웃 자체는 Tailwind 반응형 클래스(min-[768px]:...)로 CSS만으로 처리하고, 이 훅은 JS 분기가
// 꼭 필요한 곳(차트 tick 개수 축소, 조건부 API 호출 스킵)에서만 쓴다.
const QUERY = "(max-width: 767px)";

function subscribe(callback: () => void): () => void {
  if (typeof window === "undefined" || !window.matchMedia) return () => {};
  const mql = window.matchMedia(QUERY);
  mql.addEventListener("change", callback);
  return () => mql.removeEventListener("change", callback);
}

function getSnapshot(): boolean {
  return window.matchMedia(QUERY).matches;
}

function getServerSnapshot(): boolean {
  return false;
}

export function useIsMobileViewport(): boolean {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}
