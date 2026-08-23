// 측정값 응답의 simulated:true를 화면에 표기하는 공용 배지(contract §4.11 필수 요건 — 실기기가
// 없어 가상 장비 시뮬레이터가 생성한 값이므로 실측인 척 보여주지 않는다).
// simulated=false(향후 실기기 연동 시 source=DEVICE 집계)면 아무것도 렌더하지 않는다.
export default function SimulatedBadge({ simulated }: { simulated: boolean }) {
  if (!simulated) return null;
  return (
    <span className="inline-flex items-center gap-1 rounded-full border border-amber-300 bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-300">
      시뮬레이션 데이터
    </span>
  );
}
