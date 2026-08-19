package com.smartfarm.service.service;

import com.smartfarm.service.dto.UserResponse;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.entity.User;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.FarmMemberRepository;
import com.smartfarm.service.repository.RefreshTokenRepository;
import com.smartfarm.service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final FarmMemberRepository farmMemberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final FarmMemberService farmMemberService;

    public UserResponse findMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.A004));
        return UserResponse.from(user);
    }

    /**
     * 회원 탈퇴 (contract §3 탈퇴 절) — 짧은 단일 트랜잭션, 외부 호출 없음.
     *
     * <p>순서: OWNER 검사(A006) → 본인 farm_members 전부 삭제(각 농장 활성 초대 무효화 동반)
     * → 전 refresh token 무효화 → User soft delete(@SQLDelete).
     *
     * <p>토큰 무효화 관계(수용된 트레이드오프, contract §1):
     * <ul>
     *   <li>refresh — revokeAllByUserId로 즉시 전부 무효화. soft delete 이후에는
     *       AuthService#refresh의 유저 미존재(@SQLRestriction) 차단이 이중으로 막는다(#10).</li>
     *   <li>access — stateless JWT라 발급분은 만료(최대 30분)까지 서명 검증을 통과하지만,
     *       me 등 유저 조회는 soft delete 직후부터 A004로 차단되고, farm 접근은 이 트랜잭션의
     *       멤버십 삭제로 즉시 F002 차단된다 — 잔존 access 토큰으로 도달 가능한 자원이 없다.</li>
     * </ul>
     *
     * <p>OWNER 검사는 살아있는 농장 기준(existsLiveFarmMembershipByUserIdAndRole) —
     * farm soft delete가 farm_members를 지우지 않으므로, 농장 삭제를 마친 OWNER의
     * 잔존 멤버십 행이 탈퇴를 막지 않아야 한다(A006 안내대로 "농장 삭제 후 재시도" 보장).
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.A004));
        if (farmMemberRepository.existsLiveFarmMembershipByUserIdAndRole(userId, FarmRole.OWNER)) {
            throw new CustomException(ErrorCode.A006);
        }
        farmMemberService.removeAllMemberships(userId);
        refreshTokenRepository.revokeAllByUserId(userId);
        // 위 벌크 UPDATE(clearAutomatically)로 user는 detach 상태 — delete는 merge 후
        // @SQLDelete UPDATE(deleted_at = NOW())로 수행된다.
        userRepository.delete(user);
    }
}
