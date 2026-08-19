package com.smartfarm.service.service;

import com.smartfarm.service.dto.MemberResponse;
import com.smartfarm.service.entity.FarmMember;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.FarmMemberRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FarmMemberService {

    private final FarmMemberRepository farmMemberRepository;
    private final FarmAccessGuard farmAccessGuard;

    public List<MemberResponse> findMembers(Long farmId, Long userId) {
        farmAccessGuard.requireMember(farmId, userId);
        return farmMemberRepository.findMembersByFarmId(farmId);
    }

    /**
     * 멤버 제거 — OWNER는 타 멤버 제거, MEMBER는 본인 탈퇴만 (contract §3).
     * <ul>
     *   <li>OWNER 본인 제거 → F006 (농장 삭제로만 가능)</li>
     *   <li>OWNER + 대상 미존재(farm 스코프 밖 memberId 포함) → 204 no-op (멱등 DELETE,
     *       farm 스코프 조회라 타 농장 멤버십은 조회·삭제 불가)</li>
     *   <li>MEMBER + 대상이 본인 아님(미존재 포함) → F003</li>
     * </ul>
     */
    @Transactional
    public void removeMember(Long farmId, Long userId, Long memberId) {
        FarmMember requester = farmAccessGuard.requireMember(farmId, userId).membership();
        // farm 스코프 필수 조회 — cross-tenant memberId는 여기서 걸러짐
        Optional<FarmMember> target = farmMemberRepository.findByIdAndFarmId(memberId, farmId);

        if (requester.getRole() == FarmRole.OWNER) {
            if (target.isEmpty()) {
                return;
            }
            if (target.get().getUserId().equals(userId)) {
                throw new CustomException(ErrorCode.F006);
            }
            farmMemberRepository.delete(target.get());
            return;
        }

        if (target.isEmpty() || !target.get().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.F003);
        }
        farmMemberRepository.delete(target.get());
    }
}
