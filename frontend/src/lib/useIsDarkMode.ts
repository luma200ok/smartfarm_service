"use client";

import { useSyncExternalStore } from "react";

// ThemeToggle(components/ui/ThemeToggle.tsx)이 document.documentElement에 .dark 클래스를
// 토글하는 방식과 동일한 기준을 다른 컴포넌트(예: 차트 색상)에서도 읽기 위한 훅.
// ThemeToggle의 모듈 스코프 pub-sub은 그 파일 밖으로 노출되지 않으므로, 여기서는
// MutationObserver로 클래스 변화를 독립적으로 구독한다(이슈 #53 — 환경 시계열 차트).
function subscribe(callback: () => void): () => void {
  if (typeof document === "undefined") return () => {};
  const observer = new MutationObserver(callback);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
  return () => observer.disconnect();
}

function getSnapshot(): boolean {
  return document.documentElement.classList.contains("dark");
}

function getServerSnapshot(): boolean {
  return false;
}

export function useIsDarkMode(): boolean {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}
