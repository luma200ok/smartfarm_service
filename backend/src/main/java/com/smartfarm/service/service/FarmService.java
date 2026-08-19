package com.smartfarm.service.service;

import com.smartfarm.service.dto.FarmRequest;
import com.smartfarm.service.dto.FarmResponse;
import com.smartfarm.service.dto.FarmSummaryResponse;
import com.smartfarm.service.dto.FarmUpdateRequest;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.entity.FarmMember;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.repository.FarmMemberRepository;
import com.smartfarm.service.repository.FarmRepository;
import com.smartfarm.service.service.FarmAccessGuard.FarmAccess;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FarmService {

    private final FarmRepository farmRepository;
    private final FarmMemberRepository farmMemberRepository;
    private final FarmAccessGuard farmAccessGuard;

    @Transactional
    public FarmResponse createFarm(Long userId, FarmRequest request) {
        Farm farm = farmRepository.save(Farm.builder()
                .name(request.name())
                .cropType(request.cropType())
                .location(request.location())
                .build());
        farmMemberRepository.save(FarmMember.builder()
                .farmId(farm.getId())
                .userId(userId)
                .role(FarmRole.OWNER)
                .build());
        return FarmResponse.of(farm, FarmRole.OWNER, 1);
    }

    public List<FarmSummaryResponse> findMyFarms(Long userId) {
        return farmMemberRepository.findMyFarms(userId);
    }

    public FarmResponse findFarm(Long farmId, Long userId) {
        FarmAccess access = farmAccessGuard.requireMember(farmId, userId);
        return FarmResponse.of(access.farm(), access.membership().getRole(),
                farmMemberRepository.countByFarmId(farmId));
    }

    @Transactional
    public FarmResponse updateFarm(Long farmId, Long userId, FarmUpdateRequest request) {
        FarmAccess access = farmAccessGuard.requireOwner(farmId, userId);
        access.farm().update(request.name(), request.cropType(), request.location());
        return FarmResponse.of(access.farm(), FarmRole.OWNER,
                farmMemberRepository.countByFarmId(farmId));
    }

    @Transactional
    public void deleteFarm(Long farmId, Long userId) {
        FarmAccess access = farmAccessGuard.requireOwner(farmId, userId);
        // @SQLDelete → soft delete (deleted_at 갱신)
        farmRepository.delete(access.farm());
    }
}
