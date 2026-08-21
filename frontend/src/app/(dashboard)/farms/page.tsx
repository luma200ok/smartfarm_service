import type { Metadata } from "next";
import DashboardHeader from "@/components/layout/DashboardHeader";
import FarmsPageClient from "@/components/farms/FarmsPageClient";

export const metadata: Metadata = {
  title: "농장 목록 | 스마트팜",
};

export default function FarmsPage() {
  return (
    <div className="flex flex-1 flex-col">
      <DashboardHeader title="농장 목록" backHref="/dashboard" />
      <FarmsPageClient />
    </div>
  );
}
