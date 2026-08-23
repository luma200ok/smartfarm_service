import type { Metadata } from "next";
import Link from "next/link";
import NutrientRecipeDetail from "@/components/nutrient/NutrientRecipeDetail";

export const metadata: Metadata = {
  title: "양액 레시피 상세 | 스마트팜",
};

export default async function NutrientRecipeDetailPage(
  props: PageProps<"/farms/[farmId]/nutrient/[recipeId]">
) {
  const { farmId, recipeId } = await props.params;
  return (
    <div className="flex flex-1 flex-col">
      <div className="px-6 pt-6">
        <Link
          href={`/farms/${farmId}/nutrient`}
          className="text-sm text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50"
        >
          ← 목록
        </Link>
      </div>
      <NutrientRecipeDetail farmId={farmId} recipeId={recipeId} />
    </div>
  );
}
