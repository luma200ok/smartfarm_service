package com.smartfarm.service.service;

import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import java.time.Duration;

/**
 * {@code GET /environment/history} range 파라미터(contract §4.6) — 기본 24h, 미인식 값은 C001.
 * bucket=null은 다운샘플 없이 원본 그대로(24h), 그 외는 DB 집계 버킷 크기(초).
 */
public enum EnvironmentHistoryRange {

    H24("24h", Duration.ofHours(24), null),
    D7("7d", Duration.ofDays(7), Duration.ofMinutes(30)),
    D30("30d", Duration.ofDays(30), Duration.ofHours(2));

    private final String queryValue;
    private final Duration window;
    private final Duration bucket;

    EnvironmentHistoryRange(String queryValue, Duration window, Duration bucket) {
        this.queryValue = queryValue;
        this.window = window;
        this.bucket = bucket;
    }

    public String queryValue() {
        return queryValue;
    }

    public Duration window() {
        return window;
    }

    /** null이면 원본(다운샘플 없음). */
    public Duration bucket() {
        return bucket;
    }

    public static EnvironmentHistoryRange from(String raw) {
        String value = (raw == null || raw.isBlank()) ? "24h" : raw;
        for (EnvironmentHistoryRange range : values()) {
            if (range.queryValue.equals(value)) {
                return range;
            }
        }
        throw new CustomException(ErrorCode.C001);
    }
}
