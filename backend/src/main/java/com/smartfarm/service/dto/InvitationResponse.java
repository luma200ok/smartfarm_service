package com.smartfarm.service.dto;

import com.smartfarm.service.entity.Invitation;
import java.time.LocalDateTime;

public record InvitationResponse(
        String code,
        LocalDateTime expiresAt
) {

    public static InvitationResponse from(Invitation invitation) {
        return new InvitationResponse(invitation.getCode(), invitation.getExpiresAt());
    }
}
