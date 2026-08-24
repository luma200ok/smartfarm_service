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
import com.smartfarm.service.repository.DeviceRepository;
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
    private final DeviceRepository deviceRepository;
    private final FarmAccessGuard farmAccessGuard;
    private final DemoAccountGuard demoAccountGuard;
    private final ControlCascadeService controlCascadeService;

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
        farmAccessGuard.requireAdmin(farmId, userId);

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
        farmAccessGuard.requireAdmin(farmId, userId);
        Zone zone = findZoneOrThrow(farmId, zoneId);
        zone.update(request.name(), request.displayOrder());
        return ZoneResponse.from(zone);
    }

    @Transactional
    public void deleteZone(Long farmId, Long userId, Long zoneId) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireAdmin(farmId, userId);
        Zone zone = findZoneOrThrow(farmId, zoneId);

        List<Rack> racks = rackRepository.findByZoneIdOrderByDisplayOrderAscIdAsc(zoneId);
        List<Long> rackIds = racks.stream().map(Rack::getId).toList();
        List<RackLevel> levels = rackIds.isEmpty()
                ? List.of()
                : rackLevelRepository.findByRackIdInOrderByLevelNoAsc(rackIds);
        ensureNoActiveDevices(zone, rackIds, levels);

        // 제어 캐스케이드(contract §4.12) — 존을 참조하는 PENDING 큐 폐기 + 목표값 soft delete.
        // 같은 트랜잭션·같은 존 잠금 하에서 처리해 "삭제 중 apply"가 고아 참조를 적용하지 못하게 한다.
        controlCascadeService.discardForZone(farmId, zone.getId());

        rackLevelRepository.deleteAll(levels);
        rackRepository.deleteAll(racks);
        zoneRepository.delete(zone);
    }

    /**
     * 존 삭제 전 하위 활성 장비 존재 확인(리뷰 P1 #89, contract §4.10 "구조 삭제 시에도 동일 규칙") —
     * 장비 위치 FK 3종(zoneId 직속·rackId 직속·rackLevelId) 전부 확인해야 샌다. 하나만 보면 예를 들어
     * 존에 직접 매단 게이트웨이(zoneId만 채움)를 놓치고 존이 삭제된다.
     */
    private void ensureNoActiveDevices(Zone zone, List<Long> rackIds, List<RackLevel> levels) {
        List<Long> levelIds = levels.stream().map(RackLevel::getId).toList();
        boolean hasActiveDevices = deviceRepository.existsByZoneId(zone.getId())
                || (!rackIds.isEmpty() && deviceRepository.existsByRackIdIn(rackIds))
                || (!levelIds.isEmpty() && deviceRepository.existsByRackLevelIdIn(levelIds));
        if (hasActiveDevices) {
            throw new CustomException(ErrorCode.R004);
        }
    }

    /** 존이 해당 farm 소속인지 재확인(cross-tenant IDOR 차단) — 미소속·미존재는 동일하게 R001. */
    private Zone findZoneOrThrow(Long farmId, Long zoneId) {
        return zoneRepository.findByIdAndFarmId(zoneId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.R001));
    }
}
