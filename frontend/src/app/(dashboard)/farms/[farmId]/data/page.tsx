import type { Metadata } from "next";
import { Suspense } from "react";
import FarmDataAnalysis from "@/components/monitoring/FarmDataAnalysis";

export const metadata: Metadata = {
  title: "데이터 분석 | 스마트팜",
};

export default async function FarmDataPage(props: PageProps<"/farms/[farmId]/data">) {
  const { farmId } = await props.params;
  // FarmDataAnalysis가 useSearchParams(?apply=id, 이슈 #144)를 쓰므로 Suspense로 감싼다
  // (LoginPage의 SignupSuccessBanner와 동일 관례).
  return (
    <Suspense fallback={null}>
      <FarmDataAnalysis farmId={farmId} />
    </Suspense>
  );
}
