// 농장 생성/합류/삭제 시 Sidebar의 "내 농장" 리스트를 갱신하기 위한 최소 pub-sub(이슈 #42 P2-1).
// Sidebar는 (dashboard) layout에 상주해 페이지 이동만으로는 리마운트되지 않으므로,
// 목록이 바뀌는 지점(FarmCreateForm 생성 성공·InvitationAcceptForm 합류 성공·FarmOverview 삭제 성공)에서
// notifyFarmsChanged()를 호출해 구독 중인 Sidebar가 listFarms()를 재조회하게 한다.
// ThemeToggle(src/components/ui/ThemeToggle.tsx)과 동일한 구독자 Set 패턴.
const listeners = new Set<() => void>();

export function subscribeFarmsChanged(callback: () => void): () => void {
  listeners.add(callback);
  return () => listeners.delete(callback);
}

export function notifyFarmsChanged(): void {
  listeners.forEach((listener) => listener());
}
