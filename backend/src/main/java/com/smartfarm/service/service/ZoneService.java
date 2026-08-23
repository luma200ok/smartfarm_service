package com.smartfarm.service.service;

import com.smartfarm.service.dto.ZoneRequest;
import com.smartfarm.service.dto.ZoneResponse;
import com.smartfarm.service.dto.ZoneTreeResponse;
import com.smartfarm.service.dto.ZoneUpdateRequest;
import com.smartfarm.service.entity.Rack;
import com.smartfarm.service.entity.RackLevel;
import com.smartfarm.service.entity.Zone;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.RackLevelRepository;
import com.smartfarm.service.repository.RackRepository;
import com.smartfarm.service.repository.ZoneRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 존(Zone) CRUD + 존 트리 조회(contract §4.10, 이슈 #89). 존 삭제는 하위 랙·층까지 함께
 * soft delete한다(JPA 컬렉션 cascade 대신 이 코드베이스 관례대로 명시적 조회+delete로 처리).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final RackRepository rackRepository;
    private final RackLevelRepository rackLevelRepository;
    private final FarmAccessGuard farmAccessGuard;
    private final DemoAccountGuard demoAccountGuard;

    /** 존+랙+층 트리 1회 조회(contract ZoneTreeResponse) — N+1 방지를 위해 IN 절 배치 조회 후 조립. */
    public ZoneTreeResponse findZoneTree(Long farmId, Long userId) {
        farmAccessGuard.requireMember(farmId, userId);

        List<Zone> zones = zoneRepository.findByFarmIdOrderByDisplayOrderAscIdAsc(farmId);
        List<Long> zoneIds = zones.stream().map(Zone::getId).toList();
        List<Rack> racks = zoneIds.isEmpty()
                ? List.of()
                : rackRepository.findByZoneIdInOrderByDisplayOrderAscIdAsc(zoneIds);
        List<Long> rackIds = racks.stream().map(Rack::getId).toList();
        List<RackLevel> levels = rackIds.isEmpty()
                ? List.of()
                : rackLevelRepository.findByRackIdInOrderByLevelNoAsc(rackIds);

        Map<Long, List<Rack>> racksByZoneId = racks.stream()
                .collect(Collectors.groupingBy(Rack::getZoneId));
        Map<Long, List<RackLevel>> levelsByRackId = levels.stream()
                .collect(Collectors.groupingBy(RackLevel::getRackId));

        return ZoneTreeResponse.of(zones, racksByZoneId, levelsByRackId);
    }

    @Transactional
    public ZoneResponse createZone(Long farmId, Long userId, ZoneRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireOwner(farmId, userId);

        Zone zone = zoneRepository.save(Zone.builder()
                .farmId(farmId)
                .name(request.name())
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .build());
        return ZoneResponse.from(zone);
    }

    @Transactional
    public ZoneResponse updateZone(Long farmId, Long userId, Long zoneId, ZoneUpdateRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireOwner(farmId, userId);
        Zone zone = findZoneOrThrow(farmId, zoneId);
        zone.update(request.name(), request.displayOrder());
        return ZoneResponse.from(zone);
    }

    @Transactional
    public void deleteZone(Long farmId, Long userId, Long zoneId) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireOwner(farmId, userId);
        Zone zone = findZoneOrThrow(farmId, zoneId);

        List<Rack> racks = rackRepository.findByZoneIdOrderByDisplayOrderAscIdAsc(zoneId);
        for (Rack rack : racks) {
            rackLevelRepository.deleteAll(rackLevelRepository.findByRackIdOrderByLevelNoAsc(rack.getId()));
        }
        rackRepository.deleteAll(racks);
        zoneRepository.delete(zone);
    }

    /** 존이 해당 farm 소속인지 재확인(cross-tenant IDOR 차단) — 미소속·미존재는 동일하게 R001. */
    private Zone findZoneOrThrow(Long farmId, Long zoneId) {
        return zoneRepository.findByIdAndFarmId(zoneId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.R001));
    }
}
