package com.smartfarm.service.dto;

import java.time.LocalDateTime;

/**
 * 초대코드 발급 응답 — code는 발급 시 1회만 반환되는 평문(DB에는 해시만 저장되어 from(Entity) 불가).
 */
public record InvitationResponse(
        String code,
        LocalDateTime expiresAt
) {
}
