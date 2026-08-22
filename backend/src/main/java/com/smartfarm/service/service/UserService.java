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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final FarmMemberRepository farmMemberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final FarmMemberService farmMemberService;
    private final PasswordEncoder passwordEncoder;
    private final DemoAccountGuard demoAccountGuard;

    public UserResponse findMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.A004));
        return UserResponse.from(user);
    }

    /**
     * 회원 탈퇴 (contract §3 탈퇴 절·탈퇴 봉쇄 ①~④) — 짧은 단일 트랜잭션, 외부 호출 없음.
     *
     * <p>순서: users 행 잠금(FOR UPDATE — 동시 탈퇴·탈퇴↔멤버십 생성 직렬화, 패자는 A004)
     * → 비밀번호 재확인(A002 — OWNER 검사보다 먼저: 비밀번호 없는 토큰 탈취자에게 농장 보유
     * 여부를 노출하지 않음) → OWNER 검사(A006) → 본인 farm_members 전부 벌크 삭제(각 농장
     * 활성 초대 무효화 동반) → 전 refresh token 무효화 → soft delete + PII 즉시 익명화.
     *
     * <p>잔존 access 토큰(stateless JWT, 최대 30분)의 봉쇄 관계 — 서명 자체는 만료까지
     * 유효하므로 아래의 다층 차단이 완료 조건이다(어느 하나로 "도달 불가"를 단정하지 않는다):
     * <ul>
     *   <li>refresh — revokeAllByUserId 즉시 무효화 + soft delete 유저 차단(#10) 이중 차단.</li>
     *   <li>유저 표면(me 등) — @SQLRestriction으로 즉시 A004.</li>
     *   <li>farm 표면 — FarmAccessGuard 멤버십 조회가 User join으로 유저 생존을 함께
     *       검증(잔존 멤버십 행이 있어도 F002).</li>
     *   <li>가드 밖 진입점(농장 생성·초대 수락) — 각 진입부 유저 생존 검사(A004)로 유령
     *       OWNER 농장 생성·멤버십 재획득 차단.</li>
     * </ul>
     *
     * <p>OWNER 검사는 살아있는 농장 기준(existsLiveFarmMembershipByUserIdAndRole) —
     * farm soft delete가 farm_members를 지우지 않으므로, 농장 삭제를 마친 OWNER의
     * 잔존 멤버십 행이 탈퇴를 막지 않아야 한다(A006 안내대로 "농장 삭제 후 재시도" 보장).
     */
    @Transactional
    public void withdraw(Long userId, String rawPassword) {
        // 데모 계정 차단(A007) — 비밀번호 검사보다 먼저(랜덤 해시라 A002가 선행되면 차단 의미가 가려짐)
        demoAccountGuard.rejectDemoAccount(userId);
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.A004));
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            // 감사 로그(브루트포스 탐지 신호) — 값·이메일 기록 금지
            log.warn("회원 탈퇴 비밀번호 불일치 — userId={}", userId);
            throw new CustomException(ErrorCode.A002);
        }
        if (farmMemberRepository.existsLiveFarmMembershipByUserIdAndRole(userId, FarmRole.OWNER)) {
            throw new CustomException(ErrorCode.A006);
        }
        int removedMemberships = farmMemberService.removeAllMemberships(userId);
        int revokedTokens = refreshTokenRepository.revokeAllByUserId(userId);
        // 위 벌크 쿼리(clearAutomatically)로 영속성 컨텍스트가 비워져 user는 detach 상태 —
        // managed 인스턴스를 다시 로드해 상태 전이를 적용한다(행 잠금은 트랜잭션 내내 유지).
        User managed = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.A004));
        managed.withdraw();
        // 감사 로그 — PII(이메일)·토큰 값 기록 금지, 식별은 userId로만
        log.info("회원 탈퇴 완료 — userId={}, 삭제 멤버십 {}건, 무효화 refresh 토큰 {}건",
                userId, removedMemberships, revokedTokens);
    }
}
