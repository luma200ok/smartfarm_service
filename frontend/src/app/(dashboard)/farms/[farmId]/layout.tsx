// 농장 상세 하위 공통 레이아웃(이슈 #43) — 자리는 유지하되 콘텐츠는 그대로 통과시킨다.
//
// 구 FarmTabsHeader(개요·진단·처방·작업일지·멤버 탭바 + 농장명)는 이슈 #136에서 제거했다 —
// 좌측 내비(이슈 #133 IA)가 완전히 같은 항목을 전부 커버해 두 곳에서 같은 기능을 제공하던
// 상태였고, 시안 04(알람 현황)에는 이 탭바가 없어 신설 화면과도 어긋났다. 농장명은 GlobalBar
// 농장 드롭다운이 이미 보여준다. 각 페이지가 필요하면 자체 페이지 제목(h1, 17px/700)을 렌더한다
// (구 DashboardHeader의 "← 뒤로"는 이슈 #133에서 이미 제거 — GlobalBar 드롭다운의 "농장 목록
// 전체 보기"가 같은 목적지를 대신한다).
export default function FarmLayout(props: LayoutProps<"/farms/[farmId]">) {
  return props.children;
}
