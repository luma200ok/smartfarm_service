package com.smartfarm.service.service;

import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;

/**
 * {@code GET /readings/series}의 {@code scope=farm|zone:{id}|rack:{id}|level:{id}} 파라미터
 * 파싱(contract §4.11). 형식 위반은 C001 — 리소스 소속 검증(R001~R003)은 파싱 이후 서비스 계층이
 * {@code FarmAccessGuard} 선례대로 별도 수행한다(이 record는 형식 해석만 책임진다).
 */
record ReadingScope(Type type, Long id) {

    enum Type {
        FARM, ZONE, RACK, LEVEL
    }

    static ReadingScope parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(ErrorCode.C001, "scope는 필수입니다.");
        }
        if ("farm".equals(raw)) {
            return new ReadingScope(Type.FARM, null);
        }
        String[] parts = raw.split(":", 2);
        if (parts.length != 2 || parts[1].isBlank()) {
            throw new CustomException(ErrorCode.C001, "scope 형식이 올바르지 않습니다.");
        }
        Type type = switch (parts[0]) {
            case "zone" -> Type.ZONE;
            case "rack" -> Type.RACK;
            case "level" -> Type.LEVEL;
            default -> throw new CustomException(ErrorCode.C001, "scope 형식이 올바르지 않습니다.");
        };
        try {
            return new ReadingScope(type, Long.parseLong(parts[1]));
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.C001, "scope 형식이 올바르지 않습니다.");
        }
    }
}
