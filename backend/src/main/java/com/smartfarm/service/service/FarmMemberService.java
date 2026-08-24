package com.smartfarm.service.service;

import com.smartfarm.service.dto.MemberResponse;
import com.smartfarm.service.entity.FarmMember;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.FarmMemberRepository;
import com.smartfarm.service.repository.FarmRepository;
import com.smartfarm.service.repository.InvitationRepository;
import com.smartfarm.service.repository.UserRepository;
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
    private final FarmRepository farmRepository;
    private final UserRepository userRepository;
    private final FarmAccessGuard farmAccessGuard;
    private final DemoAccountGuard demoAccountGuard;

    public List<MemberResponse> findMembers(Long farmId, Long userId) {
        farmAccessGuard.requireMember(farmId, userId);
        return farmMemberRepository.findMembersByFarmId(farmId);
    }

    /**
     * 멤버 역할 변경(이슈 #122) — ADMIN 전용. 초대 수락자(PENDING)의 승인도 이 경로다.
     *
     * <p>검사 순서: 데모 차단(A007) → ADMIN 자격(F003) → <b>농장 행 잠금</b> → 대상 조회(F009)
     * → 마지막 ADMIN 보호(F006) → 전이.
     *
     * <p>⚠️ <b>왜 잠그는가</b>: "관리자 수를 세어 보고 → 역할을 바꾼다"는 check-then-act다. 잠금이
     * 없으면 <b>두 ADMIN이 서로를 동시에 강등</b>할 때 양쪽 트랜잭션이 모두 "관리자 2명"을 보고
     * 통과해 관리자가 0명이 된다 — 농장은 구조 변경·멤버 관리·삭제가 영구 불가한 관리 불능
     * 상태로 고착되고, 되돌릴 수 있는 사람이 아무도 남지 않는다. 관리자 수를 바꿀 수 있는 경로
     * (이 메서드와 {@link #removeMember})가 모두 같은 농장 행을 잡으므로 서로에 대해서도
     * 직렬화된다. ({@code AlarmRuleService#createRule}의 상한 판정과 같은 관용구.)
     *
     * <p>자기 자신 강등도 같은 규칙이다 — 마지막 ADMIN이면 본인이라도 F006이다.
     */
    @Transactional
    public MemberResponse changeMemberRole(Long farmId, Long userId, Long memberId, FarmRole role) {
        // 데모 계정 차단(A007) — 공유 계정이 농장 권한 구성을 영속 변경하는 것을 막는다(contract §4.5)
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireAdmin(farmId, userId);
        lockFarm(farmId);

        // farm 스코프 필수 조회 — cross-tenant memberId는 여기서 F009로 걸러진다
        FarmMember target = farmMemberRepository.findByIdAndFarmId(memberId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.F009));
        if (target.getRole() == FarmRole.ADMIN && role != FarmRole.ADMIN) {
            requireAnotherAdminRemains(farmId);
        }
        target.changeRole(role);

        String nickname = userRepository.findById(target.getUserId())
                .map(user -> user.getNickname())
                .orElse(null);
        return MemberResponse.from(target, nickname);
    }

    /**
     * 멤버 제거 — ADMIN은 타 멤버 제거, 그 외 역할은 본인 탈퇴만 (contract §3).
     * <ul>
     *   <li>대상이 농장의 <b>마지막 ADMIN</b> → F006 (본인이든 타인이든. 관리자가 0명이 되면
     *       농장이 관리 불능이 된다. 구 계약의 "OWNER 본인 제거 → F006"이 이 규칙의 특수 사례로
     *       흡수된다 — 관리자가 여럿이면 ADMIN도 농장을 나갈 수 있다.)</li>
     *   <li>ADMIN + 대상 미존재(farm 스코프 밖 memberId 포함) → 204 no-op (멱등 DELETE,
     *       farm 스코프 조회라 타 농장 멤버십은 조회·삭제 불가)</li>
     *   <li>비ADMIN + 대상이 본인 아님(미존재 포함) → F003</li>
     * </ul>
     *
     * <p>{@link #changeMemberRole}과 같은 농장 행 잠금 안에서 <b>대상의 역할을 다시 읽어</b>
     * 판정한다 — 잠금 밖에서 읽은 역할로 판단하면 "승격 직후 본인 탈퇴" 같은 인터리빙에서
     * 마지막 관리자가 빠져나갈 수 있다.
     */
    @Transactional
    public void removeMember(Long farmId, Long userId, Long memberId) {
        // 데모 계정 차단(A007) — 타 멤버 제거·본인 탈퇴(농장 나가기) 모두 해당(contract §4.5)
        demoAccountGuard.rejectDemoAccount(userId);
        FarmMember requester = farmAccessGuard.requireMember(farmId, userId).membership();
        lockFarm(farmId);
        // farm 스코프 필수 조회 — cross-tenant memberId는 여기서 걸러짐
        Optional<FarmMember> target = farmMemberRepository.findByIdAndFarmId(memberId, farmId);

        if (requester.getRole() == FarmRole.ADMIN) {
            if (target.isEmpty()) {
                return;
            }
        } else if (target.isEmpty() || !target.get().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.F003);
        }

        FarmMember victim = target.get();
        if (victim.getRole() == FarmRole.ADMIN) {
            requireAnotherAdminRemains(farmId);
        }
        farmMemberRepository.delete(victim);
        revokeActiveInvitations(farmId);
    }

    /**
     * 농장 행을 잠근다(SELECT ... FOR UPDATE) — 관리자 수를 바꿀 수 있는 경로의 공통 진입.
     * soft delete된 농장은 빈 Optional이라 F001이다(가드에서 이미 걸러지지만, 이 잠금은
     * 가드 이후 삭제된 농장까지 방어한다).
     */
    private void lockFarm(Long farmId) {
        farmRepository.findByIdForUpdate(farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.F001));
    }

    /**
     * 이 농장에 ADMIN이 <b>2명 이상</b>인지 — 즉 대상 ADMIN 1명을 빼도 관리자가 남는지.
     * 반드시 {@link #lockFarm} 이후에 호출한다(잠금 밖 판정은 동시 강등에 뚫린다).
     */
    private void requireAnotherAdminRemains(Long farmId) {
        if (farmMemberRepository.countLiveMembersByFarmIdAndRole(farmId, FarmRole.ADMIN) <= 1) {
            throw new CustomException(ErrorCode.F006);
        }
    }

    /**
     * 회원 탈퇴 전용 — 본인 멤버십 전부 벌크 삭제 + 소속했던 각 농장의 활성 초대 전부 무효화.
     * 단건 탈퇴({@link #removeMember})와 동일한 초대 무효화 정책(revokeActiveInvitations)을
     * 재사용한다(contract §3 탈퇴 절).
     *
     * <p>userId는 인증 principal(본인)만 전달되는 내부 경로라 FarmAccessGuard를 태우지 않는다
     * — 본인 소유 행만 삭제하므로 cross-tenant 접근 여지가 없다. ADMIN 부재 검증(A006)·
     * 유저 행 잠금은 호출자(UserService#withdraw)가 선행한다.
     *
     * <p>⚠️ 이 경로는 <b>마지막 ADMIN 보호(F006)를 검사하지 않는다</b> — 검사할 필요가 없기
     * 때문이다. 호출자의 A006 가드가 ADMIN 멤버십을 하나라도 가진 유저의 탈퇴를 통째로
     * 막으므로, 여기 도달한 유저에게는 ADMIN 멤버십이 없다. A006을 완화하려면 이 메서드에
     * 농장별 마지막 ADMIN 검사(+농장 행 잠금)를 반드시 함께 넣어야 한다.
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
