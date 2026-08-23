import { NUTRIENT_PRESET_SOURCE, NUTRIENT_SAFETY_NOTICE } from "@/constants";
import type { NutrientCalculationResponse } from "@/types";

interface NutrientCalculationResultProps {
  calculation: NutrientCalculationResponse;
  // 제공되면 "이 배합으로 저장" 버튼을 노출한다(계산 미리보기 전용 — 이미 저장된 레시피
  // 상세 화면에서는 넘기지 않아 버튼이 숨겨진다).
  onSaveClick?: () => void;
}

// 계산 결과 표시 전용 컴포넌트(contract §4.9) — 계산 로직은 전부 서버가 수행하므로
// 여기서는 응답 필드를 그대로 렌더만 한다(FE 재계산 절대 금지).
// 안전 고지·출처·이온밸런스 편차 수치는 상시 노출(숨김/토글 금지 — 요구사항).
export default function NutrientCalculationResult({ calculation, onSaveClick }: NutrientCalculationResultProps) {
  return (
    <section className="flex flex-col gap-4 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">계산 결과</h3>
        {onSaveClick && (
          <button
            type="button"
            onClick={onSaveClick}
            className="rounded-md bg-zinc-900 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-zinc-700 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            이 배합으로 저장
          </button>
        )}
      </div>

      {calculation.tanks.map((tank) => (
        <div key={tank.tank} className="flex flex-col gap-2">
          <h4 className="text-xs font-semibold text-zinc-600 dark:text-zinc-400">{tank.tank}탱크</h4>
          {tank.items.length === 0 ? (
            <p className="text-sm text-zinc-400 dark:text-zinc-500">투입할 비료가 없습니다.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[360px] text-left text-sm">
                <thead>
                  <tr className="text-xs text-zinc-500 dark:text-zinc-400">
                    <th className="py-1 pr-2 font-medium">비료</th>
                    <th className="py-1 pr-2 font-medium">화학식</th>
                    <th className="py-1 font-medium">투입량(g)</th>
                  </tr>
                </thead>
                <tbody>
                  {tank.items.map((item, idx) => (
                    <tr key={`${tank.tank}-${idx}`} className="border-t border-zinc-100 dark:border-zinc-900">
                      <td className="py-1.5 pr-2 text-zinc-900 dark:text-zinc-50">{item.fertilizer}</td>
                      <td className="py-1.5 pr-2 text-zinc-500 dark:text-zinc-400">{item.formula}</td>
                      <td className="py-1.5 text-zinc-900 dark:text-zinc-50">{item.amountG.toFixed(1)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      ))}

      <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
        <div>
          <p className="text-xs text-zinc-500 dark:text-zinc-400">예상 EC</p>
          <p className="font-medium text-zinc-900 dark:text-zinc-50">{calculation.estimatedEc.toFixed(2)} dS/m</p>
        </div>
        <div>
          <p className="text-xs text-zinc-500 dark:text-zinc-400">양이온</p>
          <p className="font-medium text-zinc-900 dark:text-zinc-50">
            {calculation.ionBalance.cationMeL.toFixed(2)} me/L
          </p>
        </div>
        <div>
          <p className="text-xs text-zinc-500 dark:text-zinc-400">음이온</p>
          <p className="font-medium text-zinc-900 dark:text-zinc-50">
            {calculation.ionBalance.anionMeL.toFixed(2)} me/L
          </p>
        </div>
        <div>
          <p className="text-xs text-zinc-500 dark:text-zinc-400">이온 밸런스 편차</p>
          <p className="font-medium text-zinc-900 dark:text-zinc-50">
            {calculation.ionBalance.deviationPercent.toFixed(1)}%
          </p>
        </div>
      </div>
      <p className="text-xs text-zinc-400 dark:text-zinc-500">
        EC는 근사값입니다(단위 dS/m, 수치는 mS/cm과 동일). 이온 밸런스 편차는 17~25% 내외가 일반적인
        정상 범위입니다.
      </p>

      {calculation.warnings.length > 0 && (
        <ul className="flex flex-col gap-1 rounded-md bg-amber-50 p-3 text-sm text-amber-800 dark:bg-amber-950/40 dark:text-amber-300">
          {calculation.warnings.map((warning, idx) => (
            <li key={idx}>⚠ {warning}</li>
          ))}
        </ul>
      )}

      <div className="flex flex-col gap-1 rounded-md bg-zinc-50 p-3 text-xs text-zinc-500 dark:bg-zinc-900 dark:text-zinc-400">
        <p>{NUTRIENT_SAFETY_NOTICE}</p>
        <p>{NUTRIENT_PRESET_SOURCE}</p>
      </div>
    </section>
  );
}
