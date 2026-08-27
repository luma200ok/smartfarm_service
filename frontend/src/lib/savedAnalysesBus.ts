// 저장한 분석 생성/이름변경/삭제 시 좌측 내비 "저장한 분석" 블록(SideNav)을 갱신하기 위한 최소
// pub-sub — alarmsBus.ts(이슈 #136)와 동일한 구독자 Set 패턴. SideNav는 (dashboard) layout에
// 상주해 페이지 이동만으로는 리마운트되지 않으므로, FarmDataAnalysis가 변경 지점에서
// notifySavedAnalysesChanged()를 호출해 구독자가 재조회하게 한다.
const listeners = new Set<() => void>();

export function subscribeSavedAnalysesChanged(callback: () => void): () => void {
  listeners.add(callback);
  return () => listeners.delete(callback);
}

export function notifySavedAnalysesChanged(): void {
  listeners.forEach((listener) => listener());
}
