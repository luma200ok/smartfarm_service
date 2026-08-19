package com.smartfarm.service.dto;

import java.util.Locale;

/**
 * 이메일 정규화 단일 지점 — trim + 소문자 (contract §4).
 * DTO compact constructor에서 호출되어 Bean Validation·저장·비교 전부 정규화된 값으로 수행된다.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
