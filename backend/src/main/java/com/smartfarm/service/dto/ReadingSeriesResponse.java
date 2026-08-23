package com.smartfarm.service.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code GET /readings/series} 응답(contract §4.11) — 24h는 원본, 7d/30d는 DB 다운샘플 집계
 * (§4.6 규칙 재사용). {@code scope=farm}은 층 간 평균 1계열로 축약된다(응답 크기 상한).
 */
public record ReadingSeriesResponse(
        String range,
        String scope,
        boolean simulated,
        List<Series> series
) {

    public record Series(String metric, String unit, List<Point> points) {
    }

    public record Point(LocalDateTime at, Double value) {
    }
}
