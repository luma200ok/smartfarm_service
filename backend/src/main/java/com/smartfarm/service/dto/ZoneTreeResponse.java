package com.smartfarm.service.dto;

import com.smartfarm.service.entity.Rack;
import com.smartfarm.service.entity.RackLevel;
import com.smartfarm.service.entity.Zone;
import java.util.List;
import java.util.Map;

/**
 * 존+랙+층 트리(contract §4.10) — 랙 도면 렌더용 1회 조회. 조립은 {@link #of}가 미리 그룹핑된
 * 맵(zoneId→racks, rackId→levels)을 받아 수행한다 — N+1 방지를 위해 서비스 계층이 IN 절 배치
 * 조회로 맵을 구성해 넘긴다.
 */
public record ZoneTreeResponse(List<ZoneNode> zones) {

    public record ZoneNode(Long id, String name, Integer displayOrder, List<RackNode> racks) {
    }

    public record RackNode(Long id, String code, Integer levelCount, Integer displayOrder, List<LevelNode> levels) {
    }

    public record LevelNode(Long id, Integer levelNo, String label) {
    }

    public static ZoneTreeResponse of(List<Zone> zones, Map<Long, List<Rack>> racksByZoneId,
                                       Map<Long, List<RackLevel>> levelsByRackId) {
        List<ZoneNode> zoneNodes = zones.stream()
                .map(zone -> toZoneNode(zone, racksByZoneId.getOrDefault(zone.getId(), List.of()), levelsByRackId))
                .toList();
        return new ZoneTreeResponse(zoneNodes);
    }

    private static ZoneNode toZoneNode(Zone zone, List<Rack> racks, Map<Long, List<RackLevel>> levelsByRackId) {
        List<RackNode> rackNodes = racks.stream()
                .map(rack -> toRackNode(rack, levelsByRackId.getOrDefault(rack.getId(), List.of())))
                .toList();
        return new ZoneNode(zone.getId(), zone.getName(), zone.getDisplayOrder(), rackNodes);
    }

    private static RackNode toRackNode(Rack rack, List<RackLevel> levels) {
        List<LevelNode> levelNodes = levels.stream()
                .map(level -> new LevelNode(level.getId(), level.getLevelNo(), level.getLabel()))
                .toList();
        return new RackNode(rack.getId(), rack.getCode(), rack.getLevelCount(), rack.getDisplayOrder(), levelNodes);
    }
}
