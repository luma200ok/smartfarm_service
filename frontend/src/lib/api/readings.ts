// docs/api-contract.md §4.11(센서 측정값, 이슈 #90) 래퍼. 전부 authFetch 경유.
// 가상 장비 시뮬레이터가 적재한 값이라 응답의 simulated 플래그를 화면에 항상 표기해야 한다
// (계약 요구사항 — 실측인 척 보여주지 않는다).
import type { LevelSummaryResponse, ReadingMatrixResponse, ReadingRange, ReadingSeriesResponse, SensorMetric } from "@/types";
import { authFetch, authFetchBinary } from "./auth";
import { ENDPOINTS } from "./endpoints";

export interface ReadingSeriesParams {
  /** 최대 4개(초과 시 C001) */
  metrics: SensorMetric[];
  range?: ReadingRange;
  /** "farm" | "zone:{id}" | "rack:{id}" | "level:{id}" */
  scope: string;
}

export async function getReadingSeries(
  farmId: number | string,
  params: ReadingSeriesParams
): Promise<ReadingSeriesResponse> {
  const query = new URLSearchParams({ metrics: params.metrics.join(","), scope: params.scope });
  if (params.range) query.set("range", params.range);
  return authFetch<ReadingSeriesResponse>(`${ENDPOINTS.farms.readingsSeries(farmId)}?${query.toString()}`);
}

export interface ReadingLatestParams {
  metric: SensorMetric;
  zoneId?: number;
}

export async function getReadingLatest(
  farmId: number | string,
  params: ReadingLatestParams
): Promise<ReadingMatrixResponse> {
  const query = new URLSearchParams({ metric: params.metric });
  if (params.zoneId !== undefined) query.set("zoneId", String(params.zoneId));
  return authFetch<ReadingMatrixResponse>(`${ENDPOINTS.farms.readingsLatest(farmId)}?${query.toString()}`);
}

// CSV 내보내기(contract §4.15, 이슈 #126) — series와 동일 파라미터, 다운샘플 결과를 CSV로.
export async function exportReadingsCsv(
  farmId: number | string,
  params: ReadingSeriesParams
): Promise<{ blob: Blob; filename: string }> {
  const query = new URLSearchParams({ metrics: params.metrics.join(","), scope: params.scope });
  if (params.range) query.set("range", params.range);
  const { blob, filename } = await authFetchBinary(
    `${ENDPOINTS.farms.readingsExportCsv(farmId)}?${query.toString()}`
  );
  return { blob, filename: filename ?? `readings-${farmId}.csv` };
}

export interface LevelSummaryParams {
  /** 필수 — 생략 시 서버 C001 */
  rackId: number;
  range?: ReadingRange;
}

export async function getLevelSummary(
  farmId: number | string,
  params: LevelSummaryParams
): Promise<LevelSummaryResponse> {
  const query = new URLSearchParams({ rackId: String(params.rackId) });
  if (params.range) query.set("range", params.range);
  return authFetch<LevelSummaryResponse>(`${ENDPOINTS.farms.readingsLevelSummary(farmId)}?${query.toString()}`);
}
