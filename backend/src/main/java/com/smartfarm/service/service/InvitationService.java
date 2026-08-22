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
import com.smartfarm.service.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvitationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    /** contract §2 — 초대코드 만료 72h, 만료·폐기 전까지 다인 재사용 가능 */
    private static final Duration INVITATION_VALIDITY = Duration.ofHours(72);
    /** V2의 unique(farm_id, user_id) 제약명 — 중복 합류 race 판정에 사용 */
    static final String MEMBER_UNIQUE_CONSTRAINT = "ux_farm_members_farm_user";

    private final InvitationRepository invitationRepository;
    private final FarmRepository farmRepository;
    private final FarmMemberRepository farmMemberRepository;
    private final FarmAccessGuard farmAccessGuard;
    private final UserRepository userRepository;
    private final DemoAccountGuard demoAccountGuard;

    /**
     * 초대코드 발급 — 농장당 활성 코드 1건 (contract §2):
     * 기존 활성 코드를 전부 무효화한 뒤 신규 발급한다. 원문은 응답으로 1회만 반환, DB에는 해시만 저장.
     */
    @Transactional
    public InvitationResponse createInvitation(Long farmId, Long userId) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireOwner(farmId, userId);
        invitationRepository.revokeAllActiveByFarmId(farmId, LocalDateTime.now());
        String rawCode = generateCode();
        Invitation invitation = invitationRepository.save(Invitation.builder()
                .farmId(farmId)
                .codeHash(TokenHasher.sha256(rawCode))
                .expiresAt(LocalDateTime.now().plus(INVITATION_VALIDITY))
                .build());
        return new InvitationResponse(rawCode, invitation.getExpiresAt());
    }

    /**
     * 초대 수락 — 코드 미존재/폐기/만료/soft delete된 농장의 코드는 전부 F004로 통일
     * (수락자에게 코드 상태·농장 존재 여부를 구분해 노출하지 않음).
     *
     * <p>FarmAccessGuard를 타지 않는 진입점이라 유저 생존을 직접 검증한다
     * (contract 탈퇴 봉쇄 ① — 탈퇴 유저의 잔존 access 토큰으로 멤버십 재획득 차단).
     */
    @Transactional
    public FarmResponse acceptInvitation(Long userId, AcceptInvitationRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.A004));
        Invitation invitation = invitationRepository.findByCodeHash(TokenHasher.sha256(request.code()))
                .orElseThrow(() -> new CustomException(ErrorCode.F004));
        if (invitation.isRevoked() || invitation.isExpired()) {
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
            // 같은 유저 동시 수락 race → unique(farm_id, user_id) 위반만 F005로 변환.
            // 다른 제약(FK 등) 위반을 F005로 오분류하지 않도록 제약명 확인 후 아니면 재throw.
            if (isMemberUniqueViolation(e)) {
                throw new CustomException(ErrorCode.F005);
            }
            throw e;
        }
        return FarmResponse.of(farm, FarmRole.MEMBER,
                farmMemberRepository.countLiveMembersByFarmId(farm.getId()));
    }

    private boolean isMemberUniqueViolation(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException cve
                && cve.getConstraintName() != null
                && cve.getConstraintName().contains(MEMBER_UNIQUE_CONSTRAINT);
    }

    /** SecureRandom 32바이트 → URL-safe Base64(43자, 패딩 없음) — 추측 불가 */
    private String generateCode() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
