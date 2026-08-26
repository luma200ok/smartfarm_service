import FarmTabsHeader from "@/components/farms/FarmTabsHeader";

// 농장 상세 하위 공통 레이아웃(이슈 #43) — 개요·진단·처방·작업일지·멤버 탭 전환 중에도
// 농장명 헤더+탭바를 유지한다. 각 탭 페이지는 더 이상 자체 DashboardHeader를 렌더하지 않는다.
// 구 DashboardHeader의 "← 뒤로"(→ /farms)는 이슈 #133에서 제거했다 — GlobalBar 농장
// 드롭다운의 "농장 목록 전체 보기"가 같은 목적지를 대신한다(§133 IA: /farms는 farm 스코프 밖).
export default async function FarmLayout(props: LayoutProps<"/farms/[farmId]">) {
  const { farmId } = await props.params;
  return (
    <div className="flex flex-1 flex-col">
      <FarmTabsHeader farmId={farmId} />
      {props.children}
    </div>
  );
}
