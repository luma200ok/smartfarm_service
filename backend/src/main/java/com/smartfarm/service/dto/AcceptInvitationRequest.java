package com.smartfarm.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank(message = "초대코드는 필수입니다.")
        @Size(max = 64, message = "초대코드는 64자 이하여야 합니다.")
        String code
) {
}
