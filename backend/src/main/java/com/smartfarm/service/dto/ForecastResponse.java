package com.smartfarm.service.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 날씨예보 응답(contract §4.8) — 향후 24h, KMA 원본 응답에 실린 시간 간격 그대로 노출. */
public record ForecastResponse(
        LocalDateTime updatedAt,
        List<Point> points
) {

    public record Point(
            LocalDateTime time,
            Double temp,
            Double humidity,
            SkyCondition sky,
            Integer pop
    ) {
    }
}
