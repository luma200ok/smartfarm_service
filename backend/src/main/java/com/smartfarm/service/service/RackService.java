package com.smartfarm.service.service;

import com.smartfarm.service.dto.RackRequest;
import com.smartfarm.service.dto.RackResponse;
import com.smartfarm.service.dto.RackUpdateRequest;
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
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 랙(Rack) CRUD — 존 하위(contract §4.10, 이슈 #89). 랙 생성 시 {@code levelCount}만큼 층을
 * 자동 생성하고, levelCount 축소 시 잘려나가는 층에 장비가 매달려 있으면 R004로 거부한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RackService {

    /** V14의 unique(zone_id, code)(활성 행) 제약명 — race 판정에 사용(InvitationService 선례). */
    static final String RACK_CODE_UNIQUE_CONSTRAINT = "ux_racks_zone_id_code_active";

    private final ZoneRepository zoneRepository;
    private final RackRepository rackRepository;
    private final RackLevelRepository rackLevelRepository;
    private final DeviceRepository deviceRepository;
    private final FarmAccessGuard farmAccessGuard;
    private final DemoAccountGuard demoAccountGuard;

    @Transactional
    public RackResponse createRack(Long farmId, Long userId, Long zoneId, RackRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireOwner(farmId, userId);
        Zone zone = findZoneOrThrow(farmId, zoneId);

        if (rackRepository.existsByZoneIdAndCode(zoneId, request.code())) {
            throw new CustomException(ErrorCode.R004);
        }

        Rack rack;
        try {
            rack = rackRepository.save(Rack.builder()
                    .zoneId(zone.getId())
                    .farmId(farmId)
                    .code(request.code())
                    .levelCount(request.levelCount())
                    .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw translateRackCodeViolation(e);
        }

        createLevels(rack, 1, request.levelCount());
        return RackResponse.from(rack);
    }

    @Transactional
    public RackResponse updateRack(Long farmId, Long userId, Long rackId, RackUpdateRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireOwner(farmId, userId);
        Rack rack = findRackOrThrow(farmId, rackId);

        if (request.code() != null && rackRepository.existsByZoneIdAndCodeAndIdNot(
                rack.getZoneId(), request.code(), rack.getId())) {
            throw new CustomException(ErrorCode.R004);
        }

        if (request.levelCount() != null && !request.levelCount().equals(rack.getLevelCount())) {
            changeLevelCount(rack, request.levelCount());
        }

        try {
            rack.update(request.code(), request.displayOrder());
            // update()는 dirty checking이라 flush 전엔 UPDATE SQL이 나가지 않는다 — race 감지를
            // 위해 명시적으로 즉시 flush(create 경로의 IDENTITY save 즉시실행과 동등하게 맞춘다).
            rackRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw translateRackCodeViolation(e);
        }
        return RackResponse.from(rack);
    }

    @Transactional
    public void deleteRack(Long farmId, Long userId, Long rackId) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireOwner(farmId, userId);
        Rack rack = findRackOrThrow(farmId, rackId);

        List<RackLevel> levels = rackLevelRepository.findByRackIdOrderByLevelNoAsc(rack.getId());
        ensureNoActiveDevices(rack, levels);

        rackLevelRepository.deleteAll(levels);
        rackRepository.delete(rack);
    }

    /**
     * 랙 삭제 전 하위 활성 장비 존재 확인(리뷰 P1 #89, contract §4.10 "구조 삭제 시에도 동일 규칙") —
     * levelCount 축소와 동일하게 랙을 통째로 지울 때도 검사 없이 통과하면 안 된다. 랙 직속(rackId)과
     * 그 랙의 층(rackLevelId) 양쪽 다 확인해야 샌다(장비 위치 FK가 둘 다일 수 있음).
     */
    private void ensureNoActiveDevices(Rack rack, List<RackLevel> levels) {
        List<Long> levelIds = levels.stream().map(RackLevel::getId).toList();
        boolean hasActiveDevices = deviceRepository.existsByRackId(rack.getId())
                || (!levelIds.isEmpty() && deviceRepository.existsByRackLevelIdIn(levelIds));
        if (hasActiveDevices) {
            throw new CustomException(ErrorCode.R004);
        }
    }

    /**
     * levelCount 변경 반영 — 축소 시 잘려나가는 층에 장비가 매달려 있으면 R004(조용한 데이터 유실
     * 방지). 확대 시 새 층을 추가 생성.
     */
    private void changeLevelCount(Rack rack, int newLevelCount) {
        int currentLevelCount = rack.getLevelCount();
        if (newLevelCount < currentLevelCount) {
            List<RackLevel> removedLevels =
                    rackLevelRepository.findByRackIdAndLevelNoGreaterThan(rack.getId(), newLevelCount);
            List<Long> removedLevelIds = removedLevels.stream().map(RackLevel::getId).toList();
            if (!removedLevelIds.isEmpty() && deviceRepository.existsByRackLevelIdIn(removedLevelIds)) {
                throw new CustomException(ErrorCode.R004);
            }
            rackLevelRepository.deleteAll(removedLevels);
        } else {
            createLevels(rack, currentLevelCount + 1, newLevelCount);
        }
        rack.changeLevelCount(newLevelCount);
    }

    private void createLevels(Rack rack, int fromLevelNoInclusive, int toLevelNoInclusive) {
        for (int levelNo = fromLevelNoInclusive; levelNo <= toLevelNoInclusive; levelNo++) {
            rackLevelRepository.save(RackLevel.builder()
                    .rackId(rack.getId())
                    .farmId(rack.getFarmId())
                    .levelNo(levelNo)
                    .build());
        }
    }

    /** 존이 해당 farm 소속인지 재확인(cross-tenant IDOR 차단) — 미소속·미존재는 동일하게 R001. */
    private Zone findZoneOrThrow(Long farmId, Long zoneId) {
        return zoneRepository.findByIdAndFarmId(zoneId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.R001));
    }

    /** 랙이 해당 farm 소속인지 재확인(cross-tenant IDOR 차단) — 미소속·미존재는 동일하게 R002. */
    private Rack findRackOrThrow(Long farmId, Long rackId) {
        return rackRepository.findByIdAndFarmId(rackId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.R002));
    }

    /**
     * 같은 존 내 코드 동시 생성 race → unique(zone_id, code) 위반만 R004로 변환. 다른 제약(FK 등)
     * 위반을 오분류하지 않도록 제약명 확인 후 아니면 재throw(InvitationService#isMemberUniqueViolation
     * 선례).
     */
    private RuntimeException translateRackCodeViolation(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cve
                && cve.getConstraintName() != null
                && cve.getConstraintName().contains(RACK_CODE_UNIQUE_CONSTRAINT)) {
            return new CustomException(ErrorCode.R004);
        }
        return e;
    }
}
