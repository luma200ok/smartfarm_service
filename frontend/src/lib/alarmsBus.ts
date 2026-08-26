// 알람 확인/처리완료/전체확인 시 TopBar 배지(GlobalBar)와 좌측 내비 "최근 7일" 통계(SideNav)를
// 갱신하기 위한 최소 pub-sub — farmsBus.ts(이슈 #42 P2-1)와 동일한 구독자 Set 패턴.
// 두 컴포넌트 모두 (dashboard) layout에 상주해 페이지 이동만으로는 리마운트되지 않으므로,
// 상태를 바꾸는 지점(AlarmDetailPanel의 확인/처리완료, AlarmsPageClient의 전체확인)에서
// notifyAlarmsChanged()를 호출해 구독자가 재조회하게 한다. 메모 추가는 건수를 바꾸지 않아 대상이 아니다.
const listeners = new Set<() => void>();

export function subscribeAlarmsChanged(callback: () => void): () => void {
  listeners.add(callback);
  return () => listeners.delete(callback);
}

export function notifyAlarmsChanged(): void {
  listeners.forEach((listener) => listener());
}
