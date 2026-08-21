import type { Metadata } from "next";
import Link from "next/link";
import PrescriptionPoller from "@/components/prescriptions/PrescriptionPoller";

export const metadata: Metadata = {
  title: "처방 상세 | 스마트팜",
};

export default async function PrescriptionDetailPage(
  props: PageProps<"/farms/[farmId]/prescriptions/[prescriptionId]">
) {
  const { farmId, prescriptionId } = await props.params;
  return (
    <div className="flex flex-1 flex-col">
      <div className="px-6 pt-6">
        <Link
          href={`/farms/${farmId}/prescriptions`}
          className="text-sm text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50"
        >
          ← 목록
        </Link>
      </div>
      <PrescriptionPoller farmId={farmId} prescriptionId={prescriptionId} />
    </div>
  );
}
