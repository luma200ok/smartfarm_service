package com.smartfarm.service.service;

import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 데모 계정 파괴적 작업 차단 가드 (이슈 #49, contract §4.5) — FE 숨김은 우회 가능하므로
 * 서버측 강제 차단이 본선이다. 차단 대상(전부 403 A007): 회원 탈퇴 · 농장 생성/수정/삭제 ·
 * 웹훅 설정 · 초대코드 발급/수락 · 멤버 제거(농장 나가기 포함). 조회·진단·처방은 허용(체험 핵심).
 *
 * <p>각 차단 대상 서비스 메서드 <b>진입부에서 최우선으로</b> 호출한다 — 기존 검사(비밀번호 재확인·
 * FarmAccessGuard 멤버십·ADMIN 검사 등)보다 앞. 데모 계정은 공유 공개 계정이라 존재 유추 채널
 * 걱정이 없고, 후속 검사 순서에 따라 A002/F00x가 먼저 나가 차단 의미가 흐려지는 것을 막는다.
 * 기존 탈퇴 봉쇄(4중)·FarmAccessGuard는 무변경 — 이 가드는 그 위에 얹는 별도 검사다.
 *
 * <p>유저 미존재(탈퇴 잔존 토큰 등)는 여기서 판정하지 않고 통과시킨다 — 기존 생존 검증
 * (A004/F002 등)이 각 경로에서 그대로 처리한다(기존 응답 계약 무변경).
 */
@Component
@RequiredArgsConstructor
public class DemoAccountGuard {

    /**
     * 데모 계정 예약 이메일(contract §4.5) — 시크릿 아님(비밀번호 자체가 존재하지 않는 계정).
     * 시드(DemoAccountInitializer)와 가입 차단(AuthService#signup)이 공유하는 단일 진실.
     */
    public static final String DEMO_EMAIL = "demo@smartfarm.local";

    private final UserRepository userRepository;

    /** 데모 계정(is_demo=true)이면 {@link ErrorCode#A007}(403) — 아니면 no-op. */
    public void rejectDemoAccount(Long userId) {
        // exists 경량 쿼리 — 엔티티 로드 없이 판정(#49 리뷰 재량 항목). @SQLRestriction이 derived
        // 쿼리에도 적용되므로 soft delete 유저는 false → 기존 생존 검증(A004/F002)이 그대로 처리.
        if (userRepository.existsByIdAndIsDemoTrue(userId)) {
            throw new CustomException(ErrorCode.A007);
        }
    }
}
