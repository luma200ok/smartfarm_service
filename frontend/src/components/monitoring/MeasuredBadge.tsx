// SimulatedBadge의 반대 개념(이슈 #99 리뷰) — 이 값이 ai-server 실측(외기 KMA + 내부 제어값)
// 데이터라는 걸 표기한다. 대시보드에 §4.11 시뮬레이션 카드(SimulatedBadge)만 남으면 어떤 값이
// 실측이고 어떤 값이 가상 장비 생성값인지 구분할 수 없어져 화면 전체가 가짜처럼 보이게 된다.
export default function MeasuredBadge() {
  return (
    <span className="inline-flex items-center gap-1 rounded-full border border-emerald-300 bg-emerald-50 px-2 py-0.5 text-[11px] font-medium text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
      실측 데이터
    </span>
  );
}
