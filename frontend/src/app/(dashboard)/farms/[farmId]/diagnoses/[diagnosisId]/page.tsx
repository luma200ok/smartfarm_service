import type { Metadata } from "next";
import Link from "next/link";
import DiagnosisDetail from "@/components/diagnoses/DiagnosisDetail";

export const metadata: Metadata = {
  title: "진단 상세 | 스마트팜",
};

export default async function DiagnosisDetailPage(
  props: PageProps<"/farms/[farmId]/diagnoses/[diagnosisId]">
) {
  const { farmId, diagnosisId } = await props.params;
  return (
    <div className="flex flex-1 flex-col">
      <div className="px-6 pt-6">
        <Link
          href={`/farms/${farmId}/diagnoses`}
          className="text-sm text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50"
        >
          ← 목록
        </Link>
      </div>
      <DiagnosisDetail farmId={farmId} diagnosisId={diagnosisId} />
    </div>
  );
}
