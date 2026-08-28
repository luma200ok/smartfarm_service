"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import type { PreviewCellState as CellState } from "@/types";
import {
  Card,
  CardTitle,
  Chip,
  RackGrid,
  RackLegend,
} from "@/components/monitoring/ui";
import SimulatedBadge from "@/components/monitoring/SimulatedBadge";
import { toCellState } from "@/components/monitoring/cellState";
import { READING_CELL_STATE_LABELS, SENSOR_METRIC_LABELS } from "@/constants";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { getReadingLatest } from "@/lib/api/readings";
import { getZoneTree } from "@/lib/api/zones";
import type {
  ReadingCellState,
  ReadingMatrixResponse,
  SensorMetric,
  ZoneTreeResponse,
} from "@/types";

// 선택 셀 요약(하단 바)에 쓰는 원본 값 — RackGrid의 CellState(색상용)와 별개로 랙 코드·층·
// 실측값·원본 상태(대문자, READING_CELL_STATE_LABELS 키)를 함께 들고 있어야
// "B3랙 4층 · EC 3.1 · 경보" 같은 문구를 만들 수 있다.
interface RackCellMeta {
  rackCode: string;
  levelNo: number;
  value: number | null;
  state: ReadingCellState;
}

const METRICS: SensorMetric[] = [
  "TEMPERATURE",
  "HUMIDITY",
  "CO2",
  "EC",
  "PH",
  "PPFD",
  "POWER",
];

const LEGEND_ITEMS: { state: CellState; label: string }[] = [
  { state: "ok", label: READING_CELL_STATE_LABELS.OK },
  { state: "warning", label: READING_CELL_STATE_LABELS.WARNING },
  { state: "critical", label: READING_CELL_STATE_LABELS.CRITICAL },
  { state: "idle", label: READING_CELL_STATE_LABELS.IDLE },
];

interface FarmRackPanelProps {
  farmId: number;
  farmName: string;
  /** 모바일 홈 축약형(이슈 #147, 시안 m1-home) — 존/지표 선택기·범례·셀 선택을 빼고
   * 셀 높이 16px·gap 3px 그리드만 온도 지표로 보여준다. 기본 false(데스크톱 동작 무변경). */
  compact?: boolean;
}

// 선택한 농장의 랙 도면(이슈 #99, #142) — GET /zones(존 탭) + GET /readings/latest(랙×층 최신값)로
// 채운다. 목업의 "전체 도면" 링크(#142 handoff — 해당 화면 없음, 사이드바에서도 비활성) 같은
// 대응 API 없는 장식은 뺐다. 셀 클릭 선택 + 더블클릭 시 제어 화면 이동(#142 시안 동작 명세)을 추가.
export default function FarmRackPanel({
  farmId,
  farmName,
  compact = false,
}: FarmRackPanelProps) {
  const router = useRouter();
  const [tree, setTree] = useState<ZoneTreeResponse | null>(null);
  const [treeError, setTreeError] = useState<string | null>(null);
  const [zoneId, setZoneId] = useState<number | null>(null); // null = 전체
  const [metric, setMetric] = useState<SensorMetric>("TEMPERATURE");
  const [matrix, setMatrix] = useState<ReadingMatrixResponse | null>(null);
  const [matrixError, setMatrixError] = useState<string | null>(null);
  const [loadingMatrix, setLoadingMatrix] = useState(true);
  const [selectedCell, setSelectedCell] = useState<{
    row: number;
    col: number;
  } | null>(null);

  // 농장이 바뀌면 존 선택을 초기화(다른 농장의 zoneId를 들고 있으면 404가 난다)
  useEffect(() => {
    let cancelled = false;
    async function load() {
      setTree(null);
      setTreeError(null);
      setZoneId(null);
      try {
        const res = await getZoneTree(farmId);
        if (!cancelled) setTree(res);
      } catch (err) {
        if (!cancelled) setTreeError(resolveErrorMessage(err));
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [farmId]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoadingMatrix(true);
      setMatrixError(null);
      setSelectedCell(null); // 도면이 바뀌면 이전 셀 선택은 무의미하다(위치가 달라질 수 있음)
      try {
        const res = await getReadingLatest(farmId, {
          metric,
          zoneId: zoneId ?? undefined,
        });
        if (!cancelled) {
          setMatrix(res);
          setLoadingMatrix(false);
        }
      } catch (err) {
        if (!cancelled) {
          setMatrixError(resolveErrorMessage(err));
          setLoadingMatrix(false);
        }
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [farmId, metric, zoneId]);

  const { cells, columns, cellMeta } = useMemo(() => {
    if (!matrix || matrix.racks.length === 0) {
      return {
        cells: [] as CellState[][],
        columns: [] as string[],
        cellMeta: [] as RackCellMeta[][],
      };
    }
    const maxLevels = Math.max(...matrix.racks.map((r) => r.levels.length));
    const cols = matrix.racks.map((r) => r.code);
    const grid: CellState[][] = [];
    const meta: RackCellMeta[][] = [];
    for (let row = 0; row < maxLevels; row++) {
      const levelNo = maxLevels - row; // 상단 행 = 최상층
      const stateRow: CellState[] = [];
      const metaRow: RackCellMeta[] = [];
      matrix.racks.forEach((rack) => {
        const level = rack.levels.find((l) => l.levelNo === levelNo);
        stateRow.push(level ? toCellState(level.state) : "idle");
        metaRow.push({
          rackCode: rack.code,
          levelNo,
          value: level?.value ?? null,
          state: level?.state ?? "IDLE",
        });
      });
      grid.push(stateRow);
      meta.push(metaRow);
    }
    return { cells: grid, columns: cols, cellMeta: meta };
  }, [matrix]);

  const selectedMeta = selectedCell
    ? cellMeta[selectedCell.row]?.[selectedCell.col]
    : null;

  return (
    <Card
      className={`flex flex-col gap-3 ${compact ? "px-[14px] py-[13px]" : "px-[18px] py-4"}`}
    >
      <div className="flex flex-wrap items-center gap-2.5">
        <CardTitle size={compact ? "md" : "lg"}>
          {compact ? "랙 배치도" : `${farmName} · 랙 배치`}
        </CardTitle>
        {!compact && (
          <span className="text-[11.5px] leading-none text-dp-muted">
            상단에서 선택한 농장
          </span>
        )}
        {!compact && matrix && <SimulatedBadge simulated={matrix.simulated} />}
        {compact && tree && (
          <span className="text-[11px] leading-none text-dp-muted">
            {tree.zones.length}존 · {columns.length}랙
          </span>
        )}
        {!compact && (
          <>
            <div className="flex-1" />
            <Link
              href={`/farms/${farmId}/data`}
              className="text-[11.5px] leading-none font-semibold text-dp-green"
            >
              데이터 분석
            </Link>
            <Link
              href={`/farms/${farmId}/devices`}
              className="text-[11.5px] leading-none font-semibold text-dp-green"
            >
              장비 관리
            </Link>
          </>
        )}
      </div>

      {treeError && (
        <p className="text-[12px] leading-[1.5] text-dp-red-ink">{treeError}</p>
      )}

      {!compact && tree && (
        <div className="flex flex-wrap gap-1.5">
          <Chip
            as="button"
            size="sm"
            active={zoneId === null}
            onClick={() => setZoneId(null)}
          >
            전체
          </Chip>
          {tree.zones.map((zone) => (
            <Chip
              key={zone.id}
              as="button"
              size="sm"
              active={zoneId === zone.id}
              onClick={() => setZoneId(zone.id)}
            >
              {zone.name}
            </Chip>
          ))}
        </div>
      )}

      {!compact && (
        <div className="flex flex-wrap gap-1.5">
          {METRICS.map((m) => (
            <Chip
              key={m}
              as="button"
              size="sm"
              active={metric === m}
              onClick={() => setMetric(m)}
            >
              {SENSOR_METRIC_LABELS[m]}
            </Chip>
          ))}
        </div>
      )}

      {matrixError && (
        <p className="text-[12px] leading-[1.5] text-dp-red-ink">
          {matrixError}
        </p>
      )}
      {!matrixError && loadingMatrix && (
        <p className="text-[12px] leading-[1.5] text-dp-muted">
          불러오는 중...
        </p>
      )}
      {!matrixError &&
        !loadingMatrix &&
        matrix &&
        matrix.racks.length === 0 && (
          <p className="text-[12px] leading-[1.5] text-dp-muted">
            등록된 랙이 없습니다.
          </p>
        )}

      {!matrixError &&
        !loadingMatrix &&
        matrix &&
        matrix.racks.length > 0 &&
        compact && (
          <div className="overflow-x-auto">
            <RackGrid
              cells={cells}
              columns={columns}
              rowHeight={16}
              gapClass="gap-[3px]"
              cellClass="rounded-[3px]"
            />
          </div>
        )}

      {!matrixError &&
        !loadingMatrix &&
        matrix &&
        matrix.racks.length > 0 &&
        !compact && (
          <div className="flex flex-col gap-2">
            <div className="flex gap-3 pl-[26px]">
              <div
                className="grid flex-1 gap-[5px]"
                style={{
                  gridTemplateColumns: `repeat(${columns.length}, minmax(14px, 1fr))`,
                }}
              >
                {columns.map((c) => (
                  <span
                    key={c}
                    className="truncate text-center font-mono text-[10px] leading-none text-dp-muted"
                  >
                    {c}
                  </span>
                ))}
              </div>
            </div>
            <div className="flex max-h-[280px] min-h-[180px] flex-1 gap-3 overflow-x-auto">
              <div className="flex w-[22px] flex-none flex-col justify-around font-mono text-[10.5px] leading-none font-semibold text-dp-muted">
                {cells.map((_, i) => (
                  <span key={i}>{cells.length - i}F</span>
                ))}
              </div>
              <RackGrid
                cells={cells}
                columns={columns}
                selected={selectedCell ?? undefined}
                onCellClick={({ row, col }) => setSelectedCell({ row, col })}
                onCellDoubleClick={() =>
                  router.push(`/farms/${farmId}/control`)
                }
              />
            </div>
            <div className="flex flex-wrap items-center gap-3.5 border-t border-dp-line pt-[11px]">
              <RackLegend items={LEGEND_ITEMS} />
              {selectedMeta && (
                <>
                  <div className="flex-1" />
                  <span className="text-[11.5px] leading-none font-semibold text-dp-green-ink">
                    {selectedMeta.rackCode}랙 {selectedMeta.levelNo}층 선택됨 ·{" "}
                    {SENSOR_METRIC_LABELS[metric]}{" "}
                    {selectedMeta.value === null
                      ? "측정 없음"
                      : selectedMeta.value.toFixed(1)}{" "}
                    · {READING_CELL_STATE_LABELS[selectedMeta.state]} ·
                    더블클릭하면 제어 화면으로 이동
                  </span>
                </>
              )}
            </div>
          </div>
        )}
    </Card>
  );
}
