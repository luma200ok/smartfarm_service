package com.smartfarm.service.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code GET /api/farms/{farmId}/environment/history} 응답(contract §4.6) — 24h는 원본,
 * 7d/30d는 DB 다운샘플 집계. 빈 구간은 점을 생략한다.
 */
public record EnvironmentHistoryResponse(
        String range,
        List<Point> points
) {

    public record Point(
            LocalDateTime capturedAt,
            Double outdoorTemp,
            Double outdoorHumidity,
            Double indoorTemp,
            Double indoorHumidity
    ) {
    }
}
