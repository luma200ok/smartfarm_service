import DashboardHeader from "@/components/layout/DashboardHeader";
import FarmTabsHeader from "@/components/farms/FarmTabsHeader";

// 농장 상세 하위 공통 레이아웃(이슈 #43) — 개요·진단·처방·작업일지·멤버 탭 전환 중에도
// 농장명 헤더+탭바를 유지한다. 각 탭 페이지는 더 이상 자체 DashboardHeader를 렌더하지 않는다.
export default async function FarmLayout(props: LayoutProps<"/farms/[farmId]">) {
  const { farmId } = await props.params;
  return (
    <div className="flex flex-1 flex-col">
      <DashboardHeader backHref="/farms" />
      <FarmTabsHeader farmId={farmId} />
      {props.children}
    </div>
  );
}
