package com.smartfarm.service.dto;

import com.smartfarm.service.entity.FarmMember;
import com.smartfarm.service.entity.FarmRole;
import java.time.LocalDateTime;

/**
 * 농장 멤버 1건 (contract §3 {@code GET /api/farms/{farmId}/members}).
 *
 * <p>{@code pending}은 {@code role}에서 파생한다(이슈 #122) — 승인 대기자도 목록에 보여야
 * 관리자가 "승인할 사람이 있다"는 것을 인지할 수 있고, FE가 역할 뱃지와 승인 액션을 구분해
 * 그리려면 role 문자열 비교보다 명시적인 플래그가 안전하다.
 */
public record MemberResponse(
        Long memberId,
        Long userId,
        String nickname,
        FarmRole role,
        boolean pending,
        LocalDateTime joinedAt
) {

    /**
     * JPQL 프로젝션 생성자 — {@code pending}은 role에서 파생하므로 조회 쿼리는 선택하지 않는다
     * (파생값을 DB에 중복 저장하지 않는다).
     */
    public MemberResponse(Long memberId, Long userId, String nickname, FarmRole role,
                          LocalDateTime joinedAt) {
        this(memberId, userId, nickname, role, role == FarmRole.PENDING, joinedAt);
    }

    public static MemberResponse from(FarmMember member, String nickname) {
        return new MemberResponse(member.getId(), member.getUserId(), nickname, member.getRole(),
                member.getJoinedAt());
    }
}
