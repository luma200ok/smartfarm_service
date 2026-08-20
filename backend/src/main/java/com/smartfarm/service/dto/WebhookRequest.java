package com.smartfarm.service.dto;

import jakarta.validation.constraints.Size;

/**
 * 농장 디스코드 웹훅 설정/해제 요청(contract §3) — null=해제, 값이 있으면 discord.com/api/webhooks
 * 프리픽스만 허용(SSRF 원천 차단).
 */
public record WebhookRequest(
        @DiscordWebhookUrl
        @Size(max = 512, message = "웹훅 URL은 512자 이하여야 합니다.")
        String webhookUrl
) {
}
