// FarmDashboardMetricValue 표시값 포맷 — 대시보드 카드(FarmDashboardCard, 이슈 #142)와 모바일
// 홈(이슈 #147)이 같은 값을 같은 규칙으로 보여줘야 해서 공용으로 뺐다(중복 포맷 로직 방지).
export function formatDashboardMetricValue(
  metric: string,
  value: number | null,
): string {
  if (value === null) return "-";
  if (metric === "TEMPERATURE") return `${value.toFixed(1)}°`;
  if (metric === "HUMIDITY") return `${value.toFixed(0)}%`;
  return value.toFixed(1);
}
