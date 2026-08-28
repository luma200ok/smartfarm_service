"use client";

import { createContext, useContext } from "react";
import type { FarmSummaryResponse } from "@/types";

// AppShell(이슈 #133)이 이미 들고 있는 농장 스코프 값을 children 트리 깊숙한 곳(예: 더보기 화면,
// 이슈 #147)에서도 다시 fetch하지 않고 읽게 하는 얕은 context. GlobalBar·SideNav는 이미 props로
// 직접 받으므로 이 context는 "라우트 페이지 컴포넌트"가 소비하는 용도로만 쓴다(prop drilling 회피).
export interface AppShellContextValue {
  farms: FarmSummaryResponse[] | null;
  farmsLoadFailed: boolean;
  effectiveFarmId: string | null;
}

const AppShellContext = createContext<AppShellContextValue | null>(null);

export function AppShellContextProvider({
  value,
  children,
}: {
  value: AppShellContextValue;
  children: React.ReactNode;
}) {
  return (
    <AppShellContext.Provider value={value}>
      {children}
    </AppShellContext.Provider>
  );
}

/** Provider 밖(예: 스토리북·단독 렌더)에서 쓰면 farms=null 등 안전한 기본값을 돌려준다. */
export function useAppShellContext(): AppShellContextValue {
  const ctx = useContext(AppShellContext);
  return ctx ?? { farms: null, farmsLoadFailed: false, effectiveFarmId: null };
}
