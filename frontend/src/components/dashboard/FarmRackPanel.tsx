"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import type { CellState } from "@/components/design-preview/mock";
import { Card, CardTitle, Chip, RackGrid, RackLegend } from "@/components/design-preview/ui";
import SimulatedBadge from "@/components/monitoring/SimulatedBadge";
import { toCellState } from "@/components/monitoring/cellState";
import { READING_CELL_STATE_LABELS, SENSOR_METRIC_LABELS } from "@/constants";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { getReadingLatest } from "@/lib/api/readings";
import { getZoneTree } from "@/lib/api/zones";
import type { ReadingMatrixResponse, SensorMetric, ZoneTreeResponse } from "@/types";

const METRICS: SensorMetric[] = ["TEMPERATURE", "HUMIDITY", "CO2", "EC", "PH", "PPFD", "POWER"];

const LEGEND_ITEMS: { state: CellState; label: string }[] = [
  { state: "ok", label: READING_CELL_STATE_LABELS.OK },
  { state: "warning", label: READING_CELL_STATE_LABELS.WARNING },
  { state: "critical", label: READING_CELL_STATE_LABELS.CRITICAL },
  { state: "idle", label: READING_CELL_STATE_LABELS.IDLE },
];

interface FarmRackPanelProps {
  farmId: number;
  farmName: string;
}

// 선택한 농장의 랙 도면(이슈 #99) — GET /zones(존 탭) + GET /readings/latest(랙×층 최신값)로 채운다.
// 목업의 "전체 도면" 링크·7일 추이 같은 대응 API 없는 장식은 뺐다(handoff 요건 2).
export default function FarmRackPanel({ farmId, farmName }: FarmRackPanelProps) {
  const [tree, setTree] = useState<ZoneTreeResponse | null>(null);
  const [treeError, setTreeError] = useState<string | null>(null);
  const [zoneId, setZoneId] = useState<number | null>(null); // null = 전체
  const [metric, setMetric] = useState<SensorMetric>("TEMPERATURE");
  const [matrix, setMatrix] = useState<ReadingMatrixResponse | null>(null);
  const [matrixError, setMatrixError] = useState<string | null>(null);
  const [loadingMatrix, setLoadingMatrix] = useState(true);

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
      try {
        const res = await getReadingLatest(farmId, { metric, zoneId: zoneId ?? undefined });
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

  const { cells, columns } = useMemo(() => {
    if (!matrix || matrix.racks.length === 0) {
      return { cells: [] as CellState[][], columns: [] as string[] };
    }
    const maxLevels = Math.max(...matrix.racks.map((r) => r.levels.length));
    const cols = matrix.racks.map((r) => r.code);
    const grid: CellState[][] = [];
    for (let row = 0; row < maxLevels; row++) {
      const levelNo = maxLevels - row; // 상단 행 = 최상층
      grid.push(
        matrix.racks.map((rack) => {
          const cell = rack.levels.find((l) => l.levelNo === levelNo);
          return cell ? toCellState(cell.state) : "idle";
        })
      );
    }
    return { cells: grid, columns: cols };
  }, [matrix]);

  return (
    <Card className="flex flex-col gap-3 px-[18px] py-4">
      <div className="flex flex-wrap items-center gap-2.5">
        <CardTitle size="lg">{farmName} · 랙 배치</CardTitle>
        {matrix && <SimulatedBadge simulated={matrix.simulated} />}
        <div className="flex-1" />
        <Link href={`/farms/${farmId}/data`} className="text-[11.5px] leading-none font-semibold text-dp-green">
          데이터 분석
        </Link>
        <Link href={`/farms/${farmId}/devices`} className="text-[11.5px] leading-none font-semibold text-dp-green">
          장비 관리
        </Link>
      </div>

      {treeError && <p className="text-[12px] leading-[1.5] text-dp-red-ink">{treeError}</p>}

      {tree && (
        <div className="flex flex-wrap gap-1.5">
          <Chip as="button" size="sm" active={zoneId === null} onClick={() => setZoneId(null)}>
            전체
          </Chip>
          {tree.zones.map((zone) => (
            <Chip key={zone.id} as="button" size="sm" active={zoneId === zone.id} onClick={() => setZoneId(zone.id)}>
              {zone.name}
            </Chip>
          ))}
        </div>
      )}

      <div className="flex flex-wrap gap-1.5">
        {METRICS.map((m) => (
          <Chip key={m} as="button" size="sm" active={metric === m} onClick={() => setMetric(m)}>
            {SENSOR_METRIC_LABELS[m]}
          </Chip>
        ))}
      </div>

      {matrixError && <p className="text-[12px] leading-[1.5] text-dp-red-ink">{matrixError}</p>}
      {!matrixError && loadingMatrix && <p className="text-[12px] leading-[1.5] text-dp-muted">불러오는 중...</p>}
      {!matrixError && !loadingMatrix && matrix && matrix.racks.length === 0 && (
        <p className="text-[12px] leading-[1.5] text-dp-muted">등록된 랙이 없습니다.</p>
      )}

      {!matrixError && !loadingMatrix && matrix && matrix.racks.length > 0 && (
        <div className="flex flex-col gap-2">
          <div className="flex gap-3 pl-[26px]">
            <div className="grid flex-1 gap-[5px]" style={{ gridTemplateColumns: `repeat(${columns.length}, minmax(14px, 1fr))` }}>
              {columns.map((c) => (
                <span key={c} className="truncate text-center font-mono text-[10px] leading-none text-dp-muted">
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
            <RackGrid cells={cells} columns={columns} />
          </div>
          <div className="flex flex-wrap items-center gap-3.5 border-t border-dp-line pt-[11px]">
            <RackLegend items={LEGEND_ITEMS} />
          </div>
        </div>
      )}
    </Card>
  );
}
