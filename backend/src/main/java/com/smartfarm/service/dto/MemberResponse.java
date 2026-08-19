package com.smartfarm.service.dto;

import com.smartfarm.service.entity.FarmRole;
import java.time.LocalDateTime;

public record MemberResponse(
        Long memberId,
        Long userId,
        String nickname,
        FarmRole role,
        LocalDateTime joinedAt
) {
}
