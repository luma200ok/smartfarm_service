import { Card, CardTitle } from "@/components/monitoring/ui";
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
    <Card className="flex flex-col gap-4 px-4 py-4">
      <div className="flex items-center justify-between">
        <h3>
          <CardTitle size="lg">계산 결과</CardTitle>
        </h3>
        {onSaveClick && (
          <button
            type="button"
            onClick={onSaveClick}
            className="rounded-md bg-dp-ink px-3 py-1.5 text-sm font-medium text-dp-surface"
          >
            이 배합으로 저장
          </button>
        )}
      </div>

      {calculation.tanks.map((tank) => (
        <div key={tank.tank} className="flex flex-col gap-2">
          <h4 className="text-xs font-semibold text-dp-sub">{tank.tank}탱크</h4>
          {tank.items.length === 0 ? (
            <p className="text-sm text-dp-muted">투입할 비료가 없습니다.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[360px] text-left text-sm">
                <thead>
                  <tr className="text-xs text-dp-muted">
                    <th className="py-1 pr-2 font-medium">비료</th>
                    <th className="py-1 pr-2 font-medium">화학식</th>
                    <th className="py-1 font-medium">투입량(g)</th>
                  </tr>
                </thead>
                <tbody>
                  {tank.items.map((item, idx) => (
                    <tr key={`${tank.tank}-${idx}`} className="border-t border-dp-line">
                      <td className="py-1.5 pr-2 text-dp-ink">{item.fertilizer}</td>
                      <td className="py-1.5 pr-2 text-dp-muted">{item.formula}</td>
                      <td className="py-1.5 text-dp-ink">{item.amountG.toFixed(1)}</td>
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
          <p className="text-xs text-dp-muted">예상 EC</p>
          <p className="font-medium text-dp-ink">{calculation.estimatedEc.toFixed(2)} dS/m</p>
        </div>
        <div>
          <p className="text-xs text-dp-muted">양이온</p>
          <p className="font-medium text-dp-ink">{calculation.ionBalance.cationMeL.toFixed(2)} me/L</p>
        </div>
        <div>
          <p className="text-xs text-dp-muted">음이온</p>
          <p className="font-medium text-dp-ink">{calculation.ionBalance.anionMeL.toFixed(2)} me/L</p>
        </div>
        <div>
          <p className="text-xs text-dp-muted">이온 밸런스 편차</p>
          <p className="font-medium text-dp-ink">{calculation.ionBalance.deviationPercent.toFixed(1)}%</p>
        </div>
      </div>
      <p className="text-xs text-dp-faint">
        EC는 근사값입니다(단위 dS/m, 수치는 mS/cm과 동일). 이온 밸런스 편차는 17~25% 내외가 일반적인
        정상 범위입니다.
      </p>

      {calculation.warnings.length > 0 && (
        <ul className="flex flex-col gap-1 rounded-md border border-dp-amber-line bg-dp-amber-tint p-3 text-sm text-dp-amber-deep">
          {calculation.warnings.map((warning, idx) => (
            <li key={idx}>⚠ {warning}</li>
          ))}
        </ul>
      )}

      {/* 안전 고지·출처는 상시 노출(PR #78 리뷰로 확정) — 접거나 숨기지 않는다. */}
      <div className="flex flex-col gap-1 rounded-md bg-dp-inset p-3 text-xs text-dp-muted">
        <p>{NUTRIENT_SAFETY_NOTICE}</p>
        <p>{NUTRIENT_PRESET_SOURCE}</p>
      </div>
    </Card>
  );
}
