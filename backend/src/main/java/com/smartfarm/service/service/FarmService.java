package com.smartfarm.service.service;

import com.smartfarm.service.dto.FarmRequest;
import com.smartfarm.service.dto.FarmResponse;
import com.smartfarm.service.dto.FarmSummaryResponse;
import com.smartfarm.service.dto.FarmUpdateRequest;
import com.smartfarm.service.dto.WebhookRequest;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.entity.FarmMember;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.FarmMemberRepository;
import com.smartfarm.service.repository.FarmRepository;
import com.smartfarm.service.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final DemoAccountGuard demoAccountGuard;

    /**
     * 농장 생성은 FarmAccessGuard를 타지 않는 진입점이라 유저 생존을 직접 검증한다
     * (contract 탈퇴 봉쇄 ① — 탈퇴 유저의 잔존 access 토큰으로 유령 OWNER 농장 생성 차단).
     */
    @Transactional
    public FarmResponse createFarm(Long userId, FarmRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.A004));
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
                farmMemberRepository.countLiveMembersByFarmId(farmId));
    }

    @Transactional
    public FarmResponse updateFarm(Long farmId, Long userId, FarmUpdateRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        FarmAccess access = farmAccessGuard.requireOwner(farmId, userId);
        access.farm().update(request.name(), request.cropType(), request.location());
        return FarmResponse.of(access.farm(), FarmRole.OWNER,
                farmMemberRepository.countLiveMembersByFarmId(farmId));
    }

    /** 웹훅 설정/해제(OWNER) — URL 원문 검증은 요청 DTO(@DiscordWebhookUrl)에서 이미 끝났다. */
    @Transactional
    public FarmResponse updateWebhook(Long farmId, Long userId, WebhookRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        FarmAccess access = farmAccessGuard.requireOwner(farmId, userId);
        access.farm().updateWebhookUrl(request.webhookUrl());
        return FarmResponse.of(access.farm(), FarmRole.OWNER,
                farmMemberRepository.countLiveMembersByFarmId(farmId));
    }

    @Transactional
    public void deleteFarm(Long farmId, Long userId) {
        demoAccountGuard.rejectDemoAccount(userId);
        FarmAccess access = farmAccessGuard.requireOwner(farmId, userId);
        // @SQLDelete → soft delete (deleted_at 갱신)
        farmRepository.delete(access.farm());
    }
}
