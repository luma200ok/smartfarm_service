package com.smartfarm.service.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;

public class DiscordWebhookUrlValidator implements ConstraintValidator<DiscordWebhookUrl, String> {

    private static final String REQUIRED_SCHEME = "https";
    private static final String REQUIRED_HOST = "discord.com";
    private static final String REQUIRED_PATH_PREFIX = "/api/webhooks/";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null = 해제(contract §3)
        }
        return isDiscordWebhookUrl(value);
    }

    /**
     * SSRF 차단 검증(contract §3) — URI 파싱 기반(리뷰 P3: 문자열 {@code startsWith}보다 관용적이고
     * 회귀에 강함). scheme=https, host=discord.com(대소문자 무관), path가 {@code /api/webhooks/}로
     * 시작하고 그 뒤에 최소 1자(웹훅 id/토큰 세그먼트)가 더 있어야 유효하다. 파싱 실패(형식 오류)는
     * 무효로 취급한다.
     *
     * <p>{@link com.smartfarm.service.service.PrescriptionWebhookNotifier}가 발송 직전
     * defense-in-depth 재검증에도 그대로 재사용한다(리뷰 P2 — 쓰기 시점 DTO 검증을 우회하는 미래 경로 대비).
     */
    public static boolean isDiscordWebhookUrl(String value) {
        if (value == null) {
            return false;
        }
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            String path = uri.getPath();
            return REQUIRED_SCHEME.equalsIgnoreCase(uri.getScheme())
                    && host != null && host.equalsIgnoreCase(REQUIRED_HOST)
                    && path != null && path.startsWith(REQUIRED_PATH_PREFIX)
                    && path.length() > REQUIRED_PATH_PREFIX.length();
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
