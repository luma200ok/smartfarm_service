package com.smartfarm.service.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}
