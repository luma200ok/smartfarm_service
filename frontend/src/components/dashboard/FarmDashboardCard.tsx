"use client";

import { Card, StatusBadge } from "@/components/monitoring/ui";
import { CROP_LABELS, FARM_DASHBOARD_STATUS_LABELS, SENSOR_METRIC_LABELS } from "@/constants";
import type { FarmDashboardResponse, FarmDashboardStatus, FarmDashboardTrendPoint } from "@/types";
import type { PreviewSeverity as Severity } from "@/types";

interface FarmDashboardCardProps {
  farm: FarmDashboardResponse;
  selected: boolean;
  onSelect: () => void;
}

// 홈 대시보드 농장 카드(이슈 #142, 시안 `01-dashboard-home`) — GET /api/dashboard/farms(#139)가
// 이미 이 카드 하나에 필요한 전부(랙/층 수·상태·지표 3열·7일 추이·최신 알람)를 한 번에 준다.
// 예전 FarmStatusCard(이슈 #99)는 카드마다 getZoneTree+getDeviceSummary를 개별 호출했는데,
// 그 N+1을 없애려고 #139가 만들어졌으므로 여기서 다시 농장별 API를 부르지 않는다 — 이 컴포넌트는
// props로 받은 값만 렌더한다(추가 fetch 없음).
//
// ⚠️ 시안의 "정식 18일"(재배 사이클)·"수확 예정" pill은 렌더하지 않는다 — 백엔드에 해당
// 도메인이 없다(#130). 없는 값을 0/임의값으로 채우면 "수확 예정 없음"이라는 거짓 정보가 된다
// (#142 handoff 원칙, 선례: #128·#129·#136).

function statusTone(status: FarmDashboardStatus): Severity {
  if (status === "CRITICAL") return "critical";
  if (status === "WARNING") return "warning";
  return "done";
}

function statusLabel(status: FarmDashboardStatus, unacknowledgedAlarmCount: number): string {
  if (status === "NORMAL") return FARM_DASHBOARD_STATUS_LABELS.NORMAL;
  return `${FARM_DASHBOARD_STATUS_LABELS[status]} ${unacknowledgedAlarmCount}`;
}

function formatMetricValue(metric: string, value: number | null): string {
  if (value === null) return "-";
  if (metric === "TEMPERATURE") return `${value.toFixed(1)}°`;
  if (metric === "HUMIDITY") return `${value.toFixed(0)}%`;
  return value.toFixed(1);
}

// 7일 미니 막대 — 대표 지표(TEMPERATURE) 일별 평균을 농장 자체 주간 범위로 정규화한다.
// 목표값 기준선이 응답에 없어(백엔드 TrendPoint에 목표치 필드 없음) 절대 임계 비교 대신
// 상대(주간 최저~최고) 높이로 그린다 — 이상값(WARNING/CRITICAL)만 색으로 강조.
function trendBarHeightPercent(point: FarmDashboardTrendPoint, min: number, max: number): number {
  if (point.value === null) return 18;
  if (max - min < 0.01) return 60;
  return 24 + (72 * (point.value - min)) / (max - min);
}

function trendBarClass(point: FarmDashboardTrendPoint, index: number, total: number): string {
  if (point.value === null || point.state === "IDLE") return "bg-dp-idle";
  if (point.state === "CRITICAL") return "bg-dp-red";
  if (point.state === "WARNING") return "bg-dp-amber";
  // OK — 과거→현재로 갈수록 진하게(시안 그라데이션 취지, 토큰만 사용).
  const recency = index / Math.max(total - 1, 1);
  if (recency < 0.35) return "bg-dp-bar-past";
  if (recency < 0.7) return "bg-dp-green-line";
  return "bg-dp-green-mid";
}

export default function FarmDashboardCard({ farm, selected, onSelect }: FarmDashboardCardProps) {
  const trendValues = farm.trend7d.map((p) => p.value).filter((v): v is number => v !== null);
  const min = trendValues.length > 0 ? Math.min(...trendValues) : 0;
  const max = trendValues.length > 0 ? Math.max(...trendValues) : 0;

  return (
    <button type="button" onClick={onSelect} aria-pressed={selected} className="text-left">
      <Card
        className={`flex h-full flex-col gap-3 px-4 py-4 transition-colors ${
          selected ? "border-[1.5px] border-dp-green" : ""
        }`}
      >
        <div className="flex items-start gap-2.5">
          <div className="min-w-0 flex-1">
            <div className="truncate text-[15px] leading-[1.3] font-bold text-dp-ink">{farm.name}</div>
            <div className="mt-1 truncate text-[11.5px] leading-none text-dp-muted">
              {farm.rackCount}랙 {farm.levelCount}층 · {CROP_LABELS[farm.cropType] ?? farm.cropType}
            </div>
          </div>
          <StatusBadge
            label={statusLabel(farm.status, farm.unacknowledgedAlarmCount)}
            tone={statusTone(farm.status)}
          />
        </div>

        <div className="grid grid-cols-3 gap-2">
          {farm.metrics.map((m) => (
            <div key={m.metric}>
              <div className="text-[10.5px] leading-none font-medium text-dp-muted">
                {SENSOR_METRIC_LABELS[m.metric] ?? m.metric}
              </div>
              <div
                className={`mt-1.5 text-[17px] leading-none font-bold ${
                  m.outOfRange ? "text-dp-red-ink" : "text-dp-ink"
                }`}
              >
                {formatMetricValue(m.metric, m.value)}
              </div>
            </div>
          ))}
        </div>

        {farm.trend7d.length > 0 && (
          <div className="flex h-[34px] items-end gap-[3px]">
            {farm.trend7d.map((point, i) => (
              <span
                key={point.date}
                className={`flex-1 rounded-[2px] ${trendBarClass(point, i, farm.trend7d.length)}`}
                style={{ height: `${trendBarHeightPercent(point, min, max)}%` }}
                title={`${point.date} ${point.value === null ? "측정 없음" : point.value.toFixed(1)}`}
              />
            ))}
          </div>
        )}

        {farm.latestAlarmMessage ? (
          <div
            className={`rounded-[7px] px-[11px] py-[9px] text-[11.5px] leading-[1.5] font-medium ${
              farm.status === "CRITICAL"
                ? "bg-dp-red-tint text-dp-red-deep"
                : farm.status === "WARNING"
                  ? "bg-dp-amber-tint text-dp-amber-sub"
                  : "bg-dp-inset text-dp-sub"
            }`}
          >
            {farm.latestAlarmMessage}
          </div>
        ) : (
          <div className="rounded-[7px] bg-dp-inset px-[11px] py-[9px] text-[11.5px] leading-[1.5] font-medium text-dp-sub">
            최근 알람 없음
          </div>
        )}
      </Card>
    </button>
  );
}
