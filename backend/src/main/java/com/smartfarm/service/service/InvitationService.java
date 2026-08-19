package com.smartfarm.service.service;

import com.smartfarm.service.dto.AcceptInvitationRequest;
import com.smartfarm.service.dto.FarmResponse;
import com.smartfarm.service.dto.InvitationResponse;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.entity.FarmMember;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.entity.Invitation;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.FarmMemberRepository;
import com.smartfarm.service.repository.FarmRepository;
import com.smartfarm.service.repository.InvitationRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvitationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    /** contract §2 — 초대코드 만료 72h, 만료까지 재사용 가능(1회용 아님) */
    private static final Duration INVITATION_VALIDITY = Duration.ofHours(72);

    private final InvitationRepository invitationRepository;
    private final FarmRepository farmRepository;
    private final FarmMemberRepository farmMemberRepository;
    private final FarmAccessGuard farmAccessGuard;

    @Transactional
    public InvitationResponse createInvitation(Long farmId, Long userId) {
        farmAccessGuard.requireOwner(farmId, userId);
        Invitation invitation = invitationRepository.save(Invitation.builder()
                .farmId(farmId)
                .code(generateCode())
                .expiresAt(LocalDateTime.now().plus(INVITATION_VALIDITY))
                .build());
        return InvitationResponse.from(invitation);
    }

    /**
     * 초대 수락 — 코드 미존재/만료/soft delete된 농장의 코드는 전부 F004로 통일
     * (수락자에게 코드 상태·농장 존재 여부를 구분해 노출하지 않음).
     */
    @Transactional
    public FarmResponse acceptInvitation(Long userId, AcceptInvitationRequest request) {
        Invitation invitation = invitationRepository.findByCode(request.code())
                .orElseThrow(() -> new CustomException(ErrorCode.F004));
        if (invitation.isExpired()) {
            throw new CustomException(ErrorCode.F004);
        }
        Farm farm = farmRepository.findById(invitation.getFarmId())
                .orElseThrow(() -> new CustomException(ErrorCode.F004));

        if (farmMemberRepository.existsByFarmIdAndUserId(farm.getId(), userId)) {
            throw new CustomException(ErrorCode.F005);
        }
        try {
            farmMemberRepository.saveAndFlush(FarmMember.builder()
                    .farmId(farm.getId())
                    .userId(userId)
                    .role(FarmRole.MEMBER)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 같은 유저 동시 수락 race → unique(farm_id, user_id) 위반
            throw new CustomException(ErrorCode.F005);
        }
        return FarmResponse.of(farm, FarmRole.MEMBER, farmMemberRepository.countByFarmId(farm.getId()));
    }

    /** SecureRandom 32바이트 → URL-safe Base64(43자, 패딩 없음) — 추측 불가 */
    private String generateCode() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
