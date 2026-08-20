package com.smartfarm.service.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 디스코드 웹훅 URL 프리픽스 고정 검증(contract §3 — SSRF 원천 차단, 임의 호스트 금지).
 * null은 유효(해제) — 검증은 값이 있을 때만 프리픽스를 강제한다.
 */
@Documented
@Constraint(validatedBy = DiscordWebhookUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface DiscordWebhookUrl {

    String message() default "웹훅 URL은 https://discord.com/api/webhooks/ 로 시작해야 합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
