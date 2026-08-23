// ZoneTreeResponse(contract §4.10) 조회 결과를 화면에서 쓰기 좋은 형태로 가공하는 순수 함수.
// 랙 선택 드롭다운(데이터 화면 level-summary rackId 필수)·장비 위치 표시(관리 화면)에서 공용으로 쓴다.
import type { ZoneTreeResponse } from "@/types";

export interface FlatRack {
  rackId: number;
  rackCode: string;
  zoneId: number;
  zoneName: string;
  levelCount: number;
}

/** 존 트리를 랙 단위로 평탄화(존 표시 순서 → 랙 표시 순서 유지). */
export function flattenRacks(tree: ZoneTreeResponse): FlatRack[] {
  return tree.zones.flatMap((zone) =>
    zone.racks.map((rack) => ({
      rackId: rack.id,
      rackCode: rack.code,
      zoneId: zone.id,
      zoneName: zone.name,
      levelCount: rack.levelCount,
    }))
  );
}

export interface LocationMaps {
  zoneNameById: Map<number, string>;
  rackById: Map<number, { code: string; zoneId: number }>;
  levelById: Map<number, { label: string; rackId: number }>;
}

/** 장비 위치 3종 FK(zoneId/rackId/rackLevelId)를 사람이 읽는 문자열로 바꾸기 위한 조회 맵. */
export function buildLocationMaps(tree: ZoneTreeResponse): LocationMaps {
  const zoneNameById = new Map<number, string>();
  const rackById = new Map<number, { code: string; zoneId: number }>();
  const levelById = new Map<number, { label: string; rackId: number }>();

  for (const zone of tree.zones) {
    zoneNameById.set(zone.id, zone.name);
    for (const rack of zone.racks) {
      rackById.set(rack.id, { code: rack.code, zoneId: zone.id });
      for (const level of rack.levels) {
        levelById.set(level.id, { label: level.label, rackId: rack.id });
      }
    }
  }

  return { zoneNameById, rackById, levelById };
}

interface DeviceLocationFks {
  zoneId: number | null;
  rackId: number | null;
  rackLevelId: number | null;
}

/** 가장 깊은 FK부터 우선해 "존 · 랙 · 층" 형태로 조합. 매핑에 없으면(삭제된 구조 등) "-". */
export function describeDeviceLocation(device: DeviceLocationFks, maps: LocationMaps): string {
  if (device.rackLevelId !== null) {
    const level = maps.levelById.get(device.rackLevelId);
    if (level) {
      const rack = maps.rackById.get(level.rackId);
      const zoneName = rack ? maps.zoneNameById.get(rack.zoneId) : undefined;
      return [zoneName, rack?.code, level.label].filter(Boolean).join(" · ");
    }
  }
  if (device.rackId !== null) {
    const rack = maps.rackById.get(device.rackId);
    if (rack) {
      const zoneName = maps.zoneNameById.get(rack.zoneId);
      return [zoneName, rack.code].filter(Boolean).join(" · ");
    }
  }
  if (device.zoneId !== null) {
    return maps.zoneNameById.get(device.zoneId) ?? "-";
  }
  return "-";
}
