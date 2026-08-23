import type { Metadata } from "next";
import NutrientPageClient from "@/components/nutrient/NutrientPageClient";

export const metadata: Metadata = {
  title: "양액 배합 | 스마트팜",
};

export default async function FarmNutrientPage(props: PageProps<"/farms/[farmId]/nutrient">) {
  const { farmId } = await props.params;
  return <NutrientPageClient farmId={farmId} />;
}
