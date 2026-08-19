package com.smartfarm.service.service;

import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.entity.FarmMember;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.FarmMemberRepository;
import com.smartfarm.service.repository.FarmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 테넌트 가드 — path param {farmId}는 입력값 취급, 매 요청 멤버십 재검증(cross-tenant IDOR 차단).
 *
 * <p>검사 순서(존재 유추 채널 차단):
 * <ol>
 *   <li>멤버십 없음 → 403 F002. 농장 존재 여부와 무관하게 통일 — 미멤버(미존재 farmId 포함)는
 *       항상 같은 응답을 받으므로 farmId 열거로 농장 존재를 유추할 수 없다.</li>
 *   <li>멤버십은 있으나 농장 soft delete → 404 F001. 전(前) 멤버에게만 노출되므로 유추 채널 아님.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class FarmAccessGuard {

    private final FarmMemberRepository farmMemberRepository;
    private final FarmRepository farmRepository;

    public record FarmAccess(Farm farm, FarmMember membership) {
    }

    public FarmAccess requireMember(Long farmId, Long userId) {
        FarmMember membership = farmMemberRepository.findByFarmIdAndUserId(farmId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.F002));
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.F001));
        return new FarmAccess(farm, membership);
    }

    public FarmAccess requireOwner(Long farmId, Long userId) {
        FarmAccess access = requireMember(farmId, userId);
        if (access.membership().getRole() != FarmRole.OWNER) {
            throw new CustomException(ErrorCode.F003);
        }
        return access;
    }
}
