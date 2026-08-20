package com.smartfarm.service.service;

import com.smartfarm.service.dto.MemberResponse;
import com.smartfarm.service.entity.FarmMember;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.FarmMemberRepository;
import com.smartfarm.service.repository.InvitationRepository;
import java.time.LocalDateTime;
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
    private final InvitationRepository invitationRepository;
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
            revokeActiveInvitations(farmId);
            return;
        }

        if (target.isEmpty() || !target.get().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.F003);
        }
        farmMemberRepository.delete(target.get());
        revokeActiveInvitations(farmId);
    }

    /**
     * 회원 탈퇴 전용 — 본인 멤버십 전부 벌크 삭제 + 소속했던 각 농장의 활성 초대 전부 무효화.
     * 단건 탈퇴({@link #removeMember})와 동일한 초대 무효화 정책(revokeActiveInvitations)을
     * 재사용한다(contract §3 탈퇴 절).
     *
     * <p>userId는 인증 principal(본인)만 전달되는 내부 경로라 FarmAccessGuard를 태우지 않는다
     * — 본인 소유 행만 삭제하므로 cross-tenant 접근 여지가 없다. OWNER 부재 검증(A006)·
     * 유저 행 잠금은 호출자(UserService#withdraw)가 선행한다.
     *
     * <p>초대 무효화용 farmId는 벌크 DELETE 전에 프로젝션으로 선조회한다. 엔티티
     * select-then-deleteAll이 아닌 벌크 DELETE라 동시 탈퇴의 패자도 0건 삭제로 조용히
     * 수렴한다(500 여지 제거).
     *
     * @return 삭제된 멤버십 행 수(감사 로그용)
     */
    @Transactional
    public int removeAllMemberships(Long userId) {
        List<Long> farmIds = farmMemberRepository.findFarmIdsByUserId(userId);
        if (farmIds.isEmpty()) {
            return 0;
        }
        int removed = farmMemberRepository.deleteAllByUserId(userId);
        farmIds.forEach(this::revokeActiveInvitations);
        return removed;
    }

    /**
     * 멤버 제거(탈퇴 포함) 시 해당 농장 활성 초대코드 전부 무효화 (contract §2)
     * — 제거된 멤버가 보유한 코드로 재합류하는 것을 차단.
     */
    private void revokeActiveInvitations(Long farmId) {
        invitationRepository.revokeAllActiveByFarmId(farmId, LocalDateTime.now());
    }
}
