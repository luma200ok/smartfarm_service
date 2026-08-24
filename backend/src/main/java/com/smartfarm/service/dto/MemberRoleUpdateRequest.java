package com.smartfarm.service.dto;

import com.smartfarm.service.entity.FarmRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 멤버 역할 변경 요청(이슈 #122) — {@code PATCH /api/farms/{farmId}/members/{memberId}/role}.
 *
 * <p>{@link FarmRole#PENDING}도 지정할 수 있다 — 승인 보류로 되돌려 접근을 회수하는 수단이다.
 * 위험한 경우(농장의 마지막 ADMIN 강등)는 값 검증이 아니라 농장 단위 불변식으로 막는다(F006).
 * 알 수 없는 문자열은 Bean Validation/역직렬화 단계에서 C001로 떨어진다.
 */
public record MemberRoleUpdateRequest(
        @Schema(description = "부여할 역할", example = "OPERATOR")
        @NotNull FarmRole role
) {
}
