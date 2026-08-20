package com.smartfarm.service.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DiscordWebhookUrlValidator implements ConstraintValidator<DiscordWebhookUrl, String> {

    /** SSRF 차단 프리픽스 — contract §3 명시, 임의 호스트로 확장 금지(고정값). */
    public static final String REQUIRED_PREFIX = "https://discord.com/api/webhooks/";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null = 해제(contract §3)
        }
        return value.startsWith(REQUIRED_PREFIX) && value.length() > REQUIRED_PREFIX.length();
    }
}
